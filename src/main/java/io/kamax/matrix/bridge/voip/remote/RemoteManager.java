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

package io.kamax.matrix.bridge.voip.remote;

import io.kamax.matrix.bridge.voip.*;
import io.kamax.matrix.bridge.voip.remote.call.FreeswitchEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RemoteManager {

    private final Logger log = LoggerFactory.getLogger(RemoteManager.class);

    private String wsUrl = System.getenv("FREESWITCH_VERTO_WS_URL");
    private String wsLogin = System.getenv("FREESWITCH_VERTO_LOGIN");
    private String wsPass = System.getenv("FREESWITCH_VERTO_PASS");
    private FreeswitchEndpoint as;

    private Map<String, RemoteEndpoint> endpoints = new ConcurrentHashMap<>();

    private List<RemoteListener> listeners = new ArrayList<>();

    public RemoteManager() {
        as = new FreeswitchEndpoint(wsUrl, wsLogin, wsPass);
        as.addListener(new CallListener() {

            @Override
            public void onInvite(String from, CallInviteEvent ev) {
                log.info("Remote: Call {}: invite from {}", ev.getCallId(), from);

                RemoteEndpoint endpoint = getEndpoint(from);
                endpoints.put(ev.getCallId(), endpoint);
                listeners.forEach(l -> l.onCallCreate(endpoint, from, ev));
                endpoint.inject(from, ev);
            }

            @Override
            public void onCandidates(CallCandidatesEvent ev) {

            }

            @Override
            public void onAnswer(CallAnswerEvent ev) {

            }

            @Override
            public void onHangup(CallHangupEvent ev) {
                log.info("Remote: Call {}: hangup", ev.getCallId());

                RemoteEndpoint endpoint = endpoints.remove(ev.getCallId());
                if (Objects.isNull(endpoint)) {
                    log.info("Call {}: no endpoint", ev.getCallId());
                    listeners.forEach(l -> l.onCallDestroy(getEndpoint(wsLogin), ev));
                } else {
                    endpoint.inject(ev);
                }
            }

            @Override
            public void onClose() {

            }

        });
    }

    public void addListener(RemoteListener listener) {
        listeners.add(listener);
    }

    public RemoteEndpoint getEndpoint(String remoteId) {
        return new RemoteEndpoint(remoteId, as);
    }

}
