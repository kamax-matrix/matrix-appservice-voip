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
import io.kamax.matrix.bridge.voip.CallHangupEvent;
import io.kamax.matrix.bridge.voip.CallInviteEvent;
import io.kamax.matrix.bridge.voip.CallListener;
import io.kamax.matrix.json.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.CloseReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class FreeswitchEndpoint {

    private final Logger log = LoggerFactory.getLogger(FreeswitchEndpoint.class);

    private String id;
    private String sessionId = UUID.randomUUID().toString();
    private FreeswitchVertoClient client;

    private List<CallListener> listeners = new ArrayList<>();

    private JsonObject withObject(Consumer<JsonObject> consumer) {
        JsonObject obj = new JsonObject();
        consumer.accept(obj);
        return obj;
    }

    public FreeswitchEndpoint(String url, String id, String passwd) {
        this.id = id;

        try {
            client = new FreeswitchVertoClient();
            client.connect(url, new FreeswitchVertoHandler() {

                @Override
                public void onClose(FreeswitchVertoClient client, CloseReason reason) {
                    log.info("Freeswitch endpoint: closing");
                    close();
                }

                @Override
                public void onMessage(String method, JsonObject params, FreeswitchVertoClient.Callback callback) {
                    log.info("Incoming {} with {}", method, params);
                    if (VertoMethod.Invite.matches(method)) {
                        CallInviteEvent.Offer offer = new CallInviteEvent.Offer();
                        offer.setSdp(GsonUtil.getStringOrThrow(params, "sdp"));
                        CallInviteEvent cEv = new CallInviteEvent();
                        cEv.setCallId(GsonUtil.getStringOrThrow(params, "callID"));
                        cEv.setLifetime(60000);
                        cEv.setOffer(offer);
                        String caller = GsonUtil.getStringOrThrow(params, "caller_id_number");
                        inject(caller, cEv);
                    }

                    if (VertoMethod.Bye.matches(method)) {
                        CallHangupEvent cEv = new CallHangupEvent();
                        cEv.setCallId(GsonUtil.getStringOrThrow(params, "callID"));
                        GsonUtil.findString(params, "cause").ifPresent(cEv::setReason);
                        inject(cEv);
                    }
                }

            });

            log.info("Freeswitch login: start");
            client.sendRequest("login", withObject(obj -> {
                obj.addProperty("login", id);
                obj.addProperty("passwd", passwd);
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

    void inject(String caller, CallInviteEvent ev) {
        listeners.forEach(l -> l.onInvite(caller, ev));
    }

    void inject(CallHangupEvent ev) {
        listeners.forEach(l -> l.onHangup(ev));
    }

    public void addListener(CallListener listener) {
        listeners.add(listener);
    }

    public boolean isClosed() {
        return Objects.isNull(client) || client.isClosed();
    }

    public void handle(String destination, CallInviteEvent ev) {
        JsonObject dialogParams = new JsonObject();
        dialogParams.addProperty("callID", ev.getCallId());
        dialogParams.addProperty("destination_number", destination);
        dialogParams.addProperty("remote_caller_id_number", id);
        JsonObject data = new JsonObject();
        data.addProperty("sessId", sessionId);
        data.addProperty("sdp", ev.getOffer().getSdp());
        data.add("dialogParams", dialogParams);

        client.sendRequest(VertoMethod.Invite.getId(), data)
                .whenComplete((jsonObject, throwable) -> {
                    if (Objects.nonNull(throwable)) {
                        log.info("Call {}: Invite to Freeswitch: FAIL", ev.getCallId());

                        CallHangupEvent hEv = new CallHangupEvent();
                        hEv.setCallId(ev.getCallId());
                        hEv.setReason("Remote error");
                        if (throwable instanceof RpcException) {
                            RpcException rpcEx = (RpcException) throwable;
                            log.warn("Failure from Freeswitch: {}", rpcEx.getRaw());
                        } else {
                            log.warn("Failure from Freeswitch: {}", throwable.getMessage());
                        }
                        inject(hEv);
                    } else {
                        log.info("Call {}: Invite to Freeswitch: OK", ev.getCallId());
                    }
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
                        log.info("Call {}: Hangup to Freeswitch: FAIL", ev.getCallId());
                        if (throwable instanceof RpcException) {
                            RpcException rpcEx = (RpcException) throwable;
                            log.warn("Failure from Freeswitch: {}", rpcEx.getRaw());
                        } else {
                            log.warn("Failure from Freeswitch: {}", throwable.getMessage());
                        }
                    } else {
                        log.info("Call {}: Hangup to Freeswitch: OK", ev.getCallId());
                    }
                }));
    }

    public synchronized void close() {
        if (isClosed()) {
            return;
        }

        client.close();
        client = null;
    }

}
