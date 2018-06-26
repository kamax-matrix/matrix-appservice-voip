/*
 * matrix-appservice-voip - Matrix Bridge to VoIP/SMS
 * Copyright (C) 2018 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.matrix.bridge.voip.remote.call;

import com.google.gson.JsonObject;
import io.kamax.matrix.bridge.voip.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FreeswitchEndpoint {

    private final Logger log = LoggerFactory.getLogger(FreeswitchEndpoint.class);

    private String id;
    private String callId;
    private FreeswitchVertoClient client;
    private String sessionId;

    private List<String> candidates;
    private long candidatesLastUpdate;
    private Timer candidateTimer;
    private long candidateDelay = 2000;

    private String fsSdp;

    private List<CallListener> listeners = new ArrayList<>();

    public FreeswitchEndpoint(String id, String callId, FreeswitchVertoClient client, String sessionId) {
        this.id = id;
        this.callId = callId;
        this.client = client;
        this.sessionId = sessionId;
    }

    public void addListener(CallListener listener) {
        listeners.add(listener);
    }

    private TimerTask getCandidateTask(CompletableFuture<List<String>> c) {
        return new TimerTask() {

            @Override
            public void run() {
                long timeout = candidatesLastUpdate + candidateDelay;
                long remaining = timeout - System.currentTimeMillis();
                if (remaining > 0) {
                    log.info("Waiting for candidates timeout");
                    candidateTimer.schedule(getCandidateTask(c), remaining);
                    return;
                }

                c.complete(candidates);
            }

        };
    }

    private CompletableFuture<List<String>> awaitCandidates() {
        CompletableFuture<List<String>> c = new CompletableFuture<>();

        candidates = new ArrayList<>();
        candidateTimer = new Timer();
        candidatesLastUpdate = 0;
        candidateTimer.schedule(getCandidateTask(c), candidateDelay);

        return c;
    }

    private synchronized void injectCandidates(List<String> candidates) {
        candidatesLastUpdate = System.currentTimeMillis();
        this.candidates.addAll(candidates);
    }

    void inject(String from, CallInviteEvent ev) {
        listeners.forEach(l -> l.onInvite(from, ev));
    }

    void inject(CallSdpEvent ev) {
        log.info("Call {}: Remote SDP:", callId);
        fsSdp = ev.getSdp();
        log.info("{}", fsSdp);
    }

    void inject(CallAnswerEvent ev) {
        ev.getAnswer().setSdp(fsSdp);
        listeners.forEach(l -> l.onAnswer(ev));
    }

    void inject(CallHangupEvent ev) {
        listeners.forEach(l -> l.onHangup(ev));
    }

    public void handle(String destination, CallInviteEvent ev) {
        log.info("Call {}: to {} as {}", callId, destination, id);

        log.info("Call {}: Invite: Awaiting candidates", callId);
        awaitCandidates().thenCompose(candidates -> {
            log.info("Call {}: Invite: Adding call candidates", callId);
            candidates.forEach(c -> ev.getOffer().setSdp(ev.getOffer().getSdp() + c + "\r\n"));

            log.info("Call {}: Local SDP:\n{}", callId, ev.getOffer().getSdp());

            JsonObject dialogParams = new JsonObject();
            dialogParams.addProperty("callID", ev.getCallId());
            dialogParams.addProperty("destination_number", destination);
            dialogParams.addProperty("remote_caller_id_number", id);
            JsonObject data = new JsonObject();
            data.addProperty("sessId", sessionId);
            data.addProperty("sdp", ev.getOffer().getSdp());
            data.add("dialogParams", dialogParams);

            log.info("Call {}: Invite: Sending", callId);
            return client.sendRequest(VertoMethod.Invite.getId(), data);

        }).whenComplete((jsonObject, throwable) -> {
            if (Objects.nonNull(throwable)) {
                log.info("Call {}: Invite to Freeswitch: FAIL", callId, ev.getCallId());

                if (throwable instanceof RpcException) {
                    RpcException rpcEx = (RpcException) throwable;
                    log.warn("Call {}: Failure from Freeswitch: {}", callId, rpcEx.getRaw());
                } else {
                    log.warn("Call {}: Failure from Freeswitch: {}", callId, throwable.getMessage());
                }

                injectHangup("Remote Error");
            } else {
                log.info("Call {}: Invite to Freeswitch: OK", callId, ev.getCallId());
            }
        });
    }

    public void handle(CallCandidatesEvent ev) {
        log.info("Call {}: injecting {} candidates", callId, ev.getCandidates().size());
        injectCandidates(ev.getCandidates().stream().map(c -> "a=" + c.getCandidate()).collect(Collectors.toList()));
    }

    public void handle(CallAnswerEvent ev) {
        log.info("Call {}: Answer: Awaiting candidates", callId);

        awaitCandidates().thenCompose(cList -> {
            log.info("Call {}: Answer: Adding call candidates", callId);
            candidates.forEach(c -> ev.getAnswer().setSdp(ev.getAnswer().getSdp() + c + "\r\n"));
            log.info("Call {}: Local SDP:\n{}", callId, ev.getAnswer().getSdp());

            JsonObject dialogParams = new JsonObject();
            dialogParams.addProperty("callID", ev.getCallId());
            JsonObject params = new JsonObject();
            params.addProperty("sessId", sessionId);
            params.add("dialogParams", dialogParams);
            params.addProperty("sdp", ev.getAnswer().getSdp());

            log.info("Call {}: Answer: Sending", callId);
            return client.sendRequest(VertoMethod.Answer.getId(), params);
        }).whenComplete((data, error) -> {
            if (Objects.isNull(error)) {
                log.info("Call {}: Freeswitch: Success", callId);
                return;
            }
            if (error instanceof RpcException) {
                RpcException rpcEx = (RpcException) error;
                log.warn("Call {}: Freeswitch: Failure: {}", callId, rpcEx.getRaw());
            } else {
                log.warn("Call {}: Freeswitch: Failure: {}", callId, error.getMessage());
            }

            injectHangup("Remote Error");
        });
    }

    public void handle(CallHangupEvent ev) {
        JsonObject dialogParams = new JsonObject();
        dialogParams.addProperty("callID", ev.getCallId());
        JsonObject data = new JsonObject();
        data.addProperty("sessId", sessionId);
        data.add("dialogParams", dialogParams);
        client.sendRequest(VertoMethod.Bye.getId(), data)
                .whenComplete(((jsonObject, throwable) -> {
                    if (Objects.nonNull(throwable)) {
                        log.info("Call {}: Hangup to Freeswitch: FAIL", callId, ev.getCallId());
                        if (throwable instanceof RpcException) {
                            RpcException rpcEx = (RpcException) throwable;
                            log.warn("Call {}: Failure from Freeswitch: {}", callId, rpcEx.getRaw());
                        } else {
                            log.warn("Call {}: Failure from Freeswitch: {}", callId, throwable.getMessage());
                        }
                    } else {
                        log.info("Call {}: Hangup to Freeswitch: OK", callId, ev.getCallId());
                    }
                }));
    }

    public synchronized boolean isClosed() {
        return Objects.isNull(client);
    }

    private void injectHangup(String reason) {
        if (isClosed()) {
            return;
        }

        CallHangupEvent ev = new CallHangupEvent();
        ev.setCallId(callId);
        if (Objects.nonNull(reason)) {
            ev.setReason(reason);
        }
        listeners.forEach(l -> l.onHangup(ev));
    }

    public synchronized void close() {
        if (isClosed()) {
            return;
        }

        client = null;
        injectHangup(null);
    }

}
