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
import io.kamax.matrix.json.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ClientEndpoint
public class FreeswitchVertoClient {

    public interface Callback {

        long getId();

        void sendAnswer(JsonObject obj);

    }

    private final Logger log = LoggerFactory.getLogger(FreeswitchVertoClient.class);

    private Session session;
    private FreeswitchVertoHandler handler;
    private Map<Long, CompletableFuture<JsonObject>> callbacks;
    private AtomicLong requestIdGen;

    public void connect(String uriRaw, FreeswitchVertoHandler handler) {
        if (!isClosed()) {
            throw new IllegalStateException();
        }

        URI uri = URI.create(Objects.requireNonNull(uriRaw));

        this.handler = Objects.requireNonNull(handler);
        this.requestIdGen = new AtomicLong(1);
        this.callbacks = new ConcurrentHashMap<>();

        try {
            log.info("Connecting to {}", uriRaw);
            ContainerProvider.getWebSocketContainer().connectToServer(this, uri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        this.session = null;
        this.handler.onClose(this, reason);
    }

    @OnMessage
    public void onMessage(String message) {
        log.info("Incoming message: {}", message);
        JsonObject obj = GsonUtil.parseObj(message);

        // TODO should use some JsonRpc object instead
        long msgId = GsonUtil.getLong(obj, "id");
        Optional<JsonObject> result = GsonUtil.findObj(obj, "result");
        Optional<JsonObject> errorOpt = GsonUtil.findObj(obj, "error");
        if (errorOpt.isPresent()) {
            JsonObject error = errorOpt.get();
            long errCode = GsonUtil.getLong(error, "code");
            String errMsg = GsonUtil.getStringOrNull(error, "message");
            callbacks.get(msgId).completeExceptionally(new RpcException(errCode, errMsg, error));
        } else if (result.isPresent()) {
            callbacks.get(msgId).complete(result.get());
        } else {
            String method = GsonUtil.getStringOrThrow(obj, "method");
            JsonObject params = GsonUtil.getObj(obj, "params");
            try {
                handler.onMessage(method, params, new Callback() {

                    @Override
                    public long getId() {
                        return msgId;
                    }

                    @Override
                    public void sendAnswer(JsonObject obj) {
                        JsonObject data = new JsonObject();
                        data.addProperty("jsonrpc", "2.0");
                        data.addProperty("id", msgId);
                        data.add("result", obj);
                        send(data);
                    }

                });
            } catch (RuntimeException e) {
                log.warn("Error when processing incoming message", e);
                JsonObject error = new JsonObject();
                error.addProperty("message", e.getMessage());
                JsonObject data = new JsonObject();
                data.addProperty("jsonrpc", "2.0");
                data.addProperty("id", msgId);
                data.add("error", error);
                send(data);
            }
        }
    }

    public CompletableFuture<JsonObject> sendRequest(String method, JsonObject params) {
        long id = requestIdGen.getAndIncrement();
        CompletableFuture<JsonObject> callback = new CompletableFuture<>();
        callbacks.put(id, callback);

        JsonObject data = new JsonObject();
        data.addProperty("jsonrpc", "2.0");
        data.addProperty("id", id);
        data.addProperty("method", method);
        data.add("params", params);
        send(data);

        return callback;
    }

    public void send(String message) {
        try {
            log.info("Outgoing message: {}", message);
            getSession().getBasicRemote().sendText(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void send(JsonObject message) {
        send(GsonUtil.get().toJson(message));
    }

    private Session getSession() {
        if (isClosed()) {
            throw new IllegalStateException("Websocket is not connected");
        }

        return session;
    }

    public boolean isClosed() {
        return Objects.isNull(session);
    }

    public void close() {
        if (isClosed()) {
            return;
        }

        try {
            getSession().close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
