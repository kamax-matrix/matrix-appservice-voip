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

package io.kamax.matrix.bridge.voip;

import io.kamax.matrix.MatrixID;
import io.kamax.matrix._MatrixID;
import io.kamax.matrix.bridge.voip.config.BridgeConfig;
import io.kamax.matrix.bridge.voip.matrix.MatrixEndpoint;
import io.kamax.matrix.bridge.voip.matrix.MatrixListener;
import io.kamax.matrix.bridge.voip.matrix.MatrixManager;
import io.kamax.matrix.bridge.voip.remote.RemoteEndpoint;
import io.kamax.matrix.bridge.voip.remote.RemoteListener;
import io.kamax.matrix.bridge.voip.remote.RemoteManager;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Bridge {

    private final Logger log = LoggerFactory.getLogger(Bridge.class);

    private BridgeConfig cfg;
    private MatrixManager matrix;
    private RemoteManager remote;

    private BidiMap<_MatrixID, String> mappings = new DualHashBidiMap<>();
    private Map<String, Call> calls = new ConcurrentHashMap<>();

    public Bridge(BridgeConfig cfg, MatrixManager matrix, RemoteManager remote) {
        this.cfg = cfg;
        this.matrix = matrix;
        this.remote = remote;

        cfg.getMapping().getUsers().forEach((localId, remoteId) -> {
            _MatrixID mxId = MatrixID.from(localId, matrix.getDomain()).valid();
            log.info("Mapping {} to {}", mxId.getId(), remoteId);
            mappings.put(mxId, remoteId);
        });
    }

    private void closeCall(String id) {
        Call call = calls.remove(id);
        if (Objects.nonNull(call)) {
            call.terminate();
            log.info("Call {}: destroyed", id);
        } else {
            log.info("Call {}: destruction ignored", id);
        }
    }

    @EventListener
    public void onStart(ApplicationStartedEvent ev) {
        matrix.addListener(new MatrixListener() {

            @Override
            public void onCallCreated(MatrixEndpoint epLocal, CallInfo info) {
                RemoteEndpoint epRemote = remote.getEndpoint(info.getId(), epLocal.getChannelId(), info.getCallee());
                calls.put(info.getId(), new Call(info.getId(), epLocal, epRemote));
                log.info("Call {}: Created", info.getId());
            }

            @Override
            public void onCallDestroyed(String id) {
                closeCall(id);
            }

        });

        remote.addListener(new RemoteListener() {

            @Override
            public void onCallCreate(RemoteEndpoint endpoint, CallInfo info) {
                log.info("Remote call {} from {} to {}", info.getId(), info.getCaller(), info.getCallee());

                _MatrixID targetUser = mappings.inverseBidiMap().get(info.getCallee());
                if (Objects.isNull(targetUser)) {
                    log.warn("Call {}: No Matrix mapping found for {}: hanging up", info.getId(), info.getCallee());
                    endpoint.close();
                } else {
                    MatrixEndpoint epLocal = matrix.getOneToOneChannelTo(info.getCaller(), targetUser, info.getId());
                    calls.put(info.getId(), new Call(info.getId(), epLocal, endpoint));
                    log.info("Call {}: created", info.getId());
                }
            }

            @Override
            public void onCallDestroy(String id) {
                closeCall(id);
            }

        });
    }

}
