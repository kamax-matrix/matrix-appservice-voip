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

import io.kamax.matrix.bridge.voip.matrix.MatrixEndpoint;
import io.kamax.matrix.bridge.voip.matrix.MatrixListener;
import io.kamax.matrix.bridge.voip.matrix.MatrixManager;
import io.kamax.matrix.bridge.voip.remote.RemoteEndpoint;
import io.kamax.matrix.bridge.voip.remote.RemoteListener;
import io.kamax.matrix.bridge.voip.remote.RemoteManager;
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
    private final String incomingRoomId = System.getenv("BRIDGE_INCOMING_ROOM_ID");

    private MatrixManager matrix;
    private RemoteManager remote;

    private Map<String, Call> calls = new ConcurrentHashMap<>();

    public Bridge(MatrixManager matrix, RemoteManager remote) {
        this.matrix = matrix;
        this.remote = remote;
    }

    @EventListener
    public void onStart(ApplicationStartedEvent ev) {
        matrix.addListener(new MatrixListener() {

            @Override
            public void onCallCreated(MatrixEndpoint epLocal, String destination, CallInviteEvent callEv) {
                RemoteEndpoint epRemote = remote.getEndpoint(destination);
                calls.put(callEv.getCallId(), new Call(callEv.getCallId(), epLocal, epRemote));
                log.info("Call {}: created", callEv.getCallId());
            }

            @Override
            public void onCallDestroyed(MatrixEndpoint endpoint, CallHangupEvent callEv) {
                Call call = calls.remove(callEv.getCallId());
                if (Objects.nonNull(call)) {
                    call.terminate();
                    log.info("Call {}: destroyed", callEv.getCallId());
                } else {
                    log.info("Call {}: destruction ignored", callEv.getCallId());
                }
            }

        });

        remote.addListener(new RemoteListener() {

            @Override
            public void onCallCreate(RemoteEndpoint endpoint, String origin, CallInviteEvent ev) {
                MatrixEndpoint epLocal = matrix.getEndpoint(origin, incomingRoomId, ev.getCallId());
                calls.put(ev.getCallId(), new Call(ev.getCallId(), epLocal, endpoint));
                log.info("Call {}: created", ev.getCallId());
            }

            @Override
            public void onCallDestroy(RemoteEndpoint endpoint, CallHangupEvent ev) {
                Call call = calls.remove(ev.getCallId());
                if (Objects.nonNull(call)) {
                    call.terminate();
                    log.info("Call {}: destroyed", ev.getCallId());
                } else {
                    log.info("Call {}: destruction ignored", ev.getCallId());
                }
            }

        });
    }

}
