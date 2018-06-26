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
import io.kamax.matrix.json.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.CloseReason;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class FreeswitchManager {

    private final String wsUrl = System.getenv("FREESWITCH_VERTO_WS_URL");
    private final String wsLogin = System.getenv("FREESWITCH_VERTO_LOGIN");
    private final String wsPass = System.getenv("FREESWITCH_VERTO_PASS");

    private final Logger log = LoggerFactory.getLogger(FreeswitchManager.class);

    private String id;
    private String sessionId = UUID.randomUUID().toString();
    private FreeswitchVertoClient client;

    private Map<String, FreeswitchEndpoint> endpoints = new ConcurrentHashMap<>();

    private List<FreeswitchListener> listeners = new ArrayList<>();

    private JsonObject withObject(Consumer<JsonObject> consumer) {
        JsonObject obj = new JsonObject();
        consumer.accept(obj);
        return obj;
    }

    public FreeswitchManager() {
        this.id = wsLogin;

        try {
            client = new FreeswitchVertoClient();
            client.connect(wsUrl, new FreeswitchVertoHandler() {

                @Override
                public void onClose(FreeswitchVertoClient client, CloseReason reason) {
                    log.info("Freeswitch endpoint: closing");
                    close();
                }

                @Override
                public void onMessage(String method, JsonObject params, FreeswitchVertoClient.Callback callback) {
                    log.info("Incoming {} with {}", method, params);
                    if (VertoMethod.Invite.matches(method)) {
                        CallInviteEvent cEv = CallInviteEvent.get(
                                GsonUtil.getStringOrThrow(params, "callID"),
                                GsonUtil.getStringOrThrow(params, "sdp"),
                                60000);
                        String caller = GsonUtil.getStringOrThrow(params, "caller_id_number");

                        FreeswitchEndpoint endpoint = getEndpoint(cEv.getCallId());
                        listeners.forEach(l -> l.onCallCreate(endpoint, caller, cEv));
                        endpoint.inject(caller, cEv);

                    }

                    if (VertoMethod.Media.matches(method)) {
                        String callId = GsonUtil.getStringOrThrow(params, "callID");
                        getEndpoint(callId).inject(CallSdpEvent.get(
                                callId,
                                GsonUtil.findString(params, "sdp").orElse(""))
                        );
                    }

                    if (VertoMethod.Answer.matches(method)) {
                        String callId = GsonUtil.getStringOrThrow(params, "callID");
                        CallAnswerEvent cEv = new CallAnswerEvent();
                        cEv.setCallId(callId);
                        getEndpoint(callId).inject(cEv);
                    }

                    if (VertoMethod.Bye.matches(method)) {
                        String callId = GsonUtil.getStringOrThrow(params, "callID");
                        String cause = GsonUtil.getStringOrNull(params, "cause");
                        if ("ORIGINATOR_CANCEL".equals(cause) || "NORMAL_CLEARING".equals(cause)) cause = null;
                        getEndpoint(callId).inject(CallHangupEvent.from(
                                callId,
                                cause
                        ));
                    }
                }

            });

            log.info("Freeswitch login: start");
            client.sendRequest("login", withObject(obj -> {
                obj.addProperty("login", wsLogin);
                obj.addProperty("passwd", wsPass);
                obj.addProperty("sessId", sessionId);
            })).thenAccept(obj -> {
                log.debug("Freeswitch login: message: {}", obj);
                log.info("Freeswitch login: success");
            }).toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            log.info("Freeswitch login: error");
            throw new RuntimeException(e instanceof ExecutionException ? e.getCause() : e);
        }
    }

    public void addListener(FreeswitchListener listener) {
        listeners.add(listener);
    }

    public boolean isClosed() {
        return Objects.isNull(client) || client.isClosed();
    }

    public FreeswitchEndpoint getEndpoint(String callId) {
        return endpoints.computeIfAbsent(callId, cId -> {
            FreeswitchEndpoint endpoint = new FreeswitchEndpoint(id, cId, client, sessionId);
            endpoint.addListener(new CallListener() { // FIXME do better
                @Override
                public void onInvite(String from, CallInviteEvent ev) {

                }

                @Override
                public void onSdp(CallSdpEvent ev) {

                }

                @Override
                public void onCandidates(CallCandidatesEvent ev) {

                }

                @Override
                public void onAnswer(CallAnswerEvent ev) {

                }

                @Override
                public void onHangup(CallHangupEvent ev) {

                }

                @Override
                public void onClose() {
                    log.info("Removing endpoint for Call {}: closed", callId);
                    endpoints.remove(callId);
                }
            });
            return endpoint;
        });
    }

    public synchronized void close() {
        if (isClosed()) {
            return;
        }

        client.close();
        client = null;
    }

}
