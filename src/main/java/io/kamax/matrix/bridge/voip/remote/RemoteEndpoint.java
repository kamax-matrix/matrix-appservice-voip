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

import io.kamax.matrix.bridge.voip.CallListener;
import io.kamax.matrix.bridge.voip.CallSdpEvent;
import io.kamax.matrix.bridge.voip.EndpointListener;
import io.kamax.matrix.bridge.voip.GenericEndpoint;
import io.kamax.matrix.bridge.voip.matrix.event.CallAnswerEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallCandidatesEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallHangupEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallInviteEvent;
import io.kamax.matrix.bridge.voip.remote.call.FreeswitchEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class RemoteEndpoint extends GenericEndpoint {

    private final Logger log = LoggerFactory.getLogger(RemoteEndpoint.class);

    private FreeswitchEndpoint voip;

    public RemoteEndpoint(String userId, String channelId, String callId, FreeswitchEndpoint voip) {
        super(userId, channelId, callId);

        this.voip = voip;
        this.voip.addListener(this::doClose); // Endpoint Listener
        this.voip.addListener(new CallListener() {

            @Override
            public void onInvite(String from, CallInviteEvent ev) {
                log.info("Call {}: Remote: VoIP: Invite from {}", ev.getCallId(), from);
                inject(from, ev);
            }

            @Override
            public void onSdp(CallSdpEvent ev) {
                // We shouldn't get this ever
                log.warn("Call {}: Remote: VoIP: Got SDP event!", ev.getCallId());
            }

            @Override
            public void onCandidates(CallCandidatesEvent ev) {
                // FIXME support
            }

            @Override
            public void onAnswer(CallAnswerEvent ev) {
                log.info("Call {}: Remote: VoIP: Answer", ev.getCallId());
                inject(ev);
            }

            @Override
            public void onHangup(CallHangupEvent ev) {
                inject(ev);
            }

        });

    }

    void inject(String from, CallInviteEvent ev) {
        fireCallEvent(l -> l.onInvite(from, ev));
    }

    void inject(CallAnswerEvent ev) {
        fireCallEvent(l -> l.onAnswer(ev));
    }

    void inject(CallHangupEvent ev) {
        fireHangupEvent(ev.getReason());
        doClose();
    }

    @Override
    public void handle(String from, CallInviteEvent ev) {
        voip.handle(from, ev);
    }

    @Override
    public void handle(CallCandidatesEvent ev) {
        voip.handle(ev);
    }

    @Override
    public void handle(CallAnswerEvent ev) {
        voip.handle(ev);
    }

    @Override
    public void handle(CallHangupEvent ev) {
        voip.handle(ev);
    }

    public synchronized boolean isClosed() {
        return Objects.isNull(voip);
    }

    private synchronized void doClose() {
        if (isClosed()) {
            return;
        }

        voip = null;
        fireEndpointEvent(EndpointListener::onClose);
    }

    public void close() {
        voip.close();
    }

}
