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
import io.kamax.matrix.bridge.voip.CallSdpEvent;
import io.kamax.matrix.bridge.voip.GenericEndpoint;
import io.kamax.matrix.bridge.voip.matrix.event.CallAnswerEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallCandidatesEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallHangupEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallInviteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FreeswitchEndpoint extends GenericEndpoint {

    private final Logger log = LoggerFactory.getLogger(FreeswitchEndpoint.class);

    private FreeswitchVertoClient client;

    private List<String> candidates;
    private long candidatesLastUpdate;
    private Timer candidateTimer;
    private long candidateDelay = 2000;

    private String fsSdp;

    public FreeswitchEndpoint(String id, String callId, FreeswitchVertoClient client, String sessionId) {
        super(callId, sessionId, id);
        this.client = client;
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
        fireCallEvent(l -> l.onInvite(from, ev));
    }

    void inject(CallSdpEvent ev) {
        log.info("Call {}: Remote SDP:", getCallId());
        fsSdp = ev.getSdp();
        log.info("{}", fsSdp);
    }

    void inject(CallAnswerEvent ev) {
        ev.getAnswer().setSdp(fsSdp);
        fireCallEvent(l -> l.onAnswer(ev));
    }

    void inject(CallHangupEvent ev) {
        fireHangupEvent(ev.getReason());
    }

    public void handle(String destination, CallInviteEvent ev) {
        log.info("Call {}: from {} to {}", getCallId(), getUserId(), destination);

        log.info("Call {}: Invite: Awaiting candidates", getCallId());
        awaitCandidates().thenCompose(candidates -> {
            log.info("Call {}: Invite: Adding call candidates", getCallId());
            candidates.forEach(c -> ev.getOffer().setSdp(ev.getOffer().getSdp() + c + "\r\n"));

            log.info("Call {}: Local SDP:\n{}", getCallId(), ev.getOffer().getSdp());

            JsonObject dialogParams = new JsonObject();
            dialogParams.addProperty("callID", ev.getCallId());
            dialogParams.addProperty("destination_number", destination);
            dialogParams.addProperty("remote_caller_id_number", getUserId());
            JsonObject data = new JsonObject();
            data.addProperty("sessId", getChannelId());
            data.addProperty("sdp", ev.getOffer().getSdp());
            data.add("dialogParams", dialogParams);

            log.info("Call {}: Invite: Sending", getCallId());
            return client.sendRequest(VertoMethod.Invite.getId(), data);

        }).whenComplete((jsonObject, throwable) -> {
            if (Objects.nonNull(throwable)) {
                log.info("Call {}: Invite to Freeswitch: FAIL", getCallId(), ev.getCallId());

                if (throwable instanceof RpcException) {
                    RpcException rpcEx = (RpcException) throwable;
                    log.warn("Call {}: Failure from Freeswitch: {}", getCallId(), rpcEx.getRaw());
                } else {
                    log.warn("Call {}: Failure from Freeswitch: {}", getCallId(), throwable.getMessage());
                }

                injectHangup("Remote Error");
            } else {
                log.info("Call {}: Invite to Freeswitch: OK", getCallId(), ev.getCallId());
            }
        });
    }

    public void handle(CallCandidatesEvent ev) {
        log.info("Call {}: injecting {} candidates", getCallId(), ev.getCandidates().size());
        injectCandidates(ev.getCandidates().stream().map(c -> "a=" + c.getCandidate()).collect(Collectors.toList()));
    }

    public void handle(CallAnswerEvent ev) {
        log.info("Call {}: Answer: Awaiting candidates", getCallId());

        awaitCandidates().thenCompose(cList -> {
            log.info("Call {}: Answer: Adding call candidates", getCallId());
            candidates.forEach(c -> ev.getAnswer().setSdp(ev.getAnswer().getSdp() + c + "\r\n"));
            log.info("Call {}: Local SDP:\n{}", getCallId(), ev.getAnswer().getSdp());

            JsonObject dialogParams = new JsonObject();
            dialogParams.addProperty("callID", ev.getCallId());
            JsonObject params = new JsonObject();
            params.addProperty("sessId", getChannelId());
            params.add("dialogParams", dialogParams);
            params.addProperty("sdp", ev.getAnswer().getSdp());

            log.info("Call {}: Answer: Sending", getCallId());
            return client.sendRequest(VertoMethod.Answer.getId(), params);
        }).whenComplete((data, error) -> {
            if (Objects.isNull(error)) {
                log.info("Call {}: Freeswitch: Success", getCallId());
                return;
            }
            if (error instanceof RpcException) {
                RpcException rpcEx = (RpcException) error;
                log.warn("Call {}: Freeswitch: Failure: {}", getCallId(), rpcEx.getRaw());
            } else {
                log.warn("Call {}: Freeswitch: Failure: {}", getCallId(), error.getMessage());
            }

            injectHangup("Remote Error");
        });
    }

    public void handle(CallHangupEvent ev) {
        JsonObject dialogParams = new JsonObject();
        dialogParams.addProperty("callID", ev.getCallId());
        JsonObject data = new JsonObject();
        data.addProperty("sessId", getChannelId());
        data.add("dialogParams", dialogParams);
        client.sendRequest(VertoMethod.Bye.getId(), data)
                .whenComplete(((jsonObject, throwable) -> {
                    if (Objects.nonNull(throwable)) {
                        log.info("Call {}: Hangup to Freeswitch: FAIL", getCallId(), ev.getCallId());
                        if (throwable instanceof RpcException) {
                            RpcException rpcEx = (RpcException) throwable;
                            log.warn("Call {}: Failure from Freeswitch: {}", getCallId(), rpcEx.getRaw());
                        } else {
                            log.warn("Call {}: Failure from Freeswitch: {}", getCallId(), throwable.getMessage());
                        }
                    } else {
                        log.info("Call {}: Hangup to Freeswitch: OK", getCallId(), ev.getCallId());
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

        fireHangupEvent(reason);
    }

    public synchronized void close() {
        if (isClosed()) {
            return;
        }

        client = null;
        injectHangup(null);
    }

}
