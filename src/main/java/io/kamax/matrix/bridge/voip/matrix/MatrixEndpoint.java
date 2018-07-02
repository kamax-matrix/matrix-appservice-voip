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

package io.kamax.matrix.bridge.voip.matrix;

import io.kamax.matrix.bridge.voip.EndpointListener;
import io.kamax.matrix.bridge.voip.GenericEndpoint;
import io.kamax.matrix.bridge.voip.matrix.event.CallAnswerEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallCandidatesEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallHangupEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallInviteEvent;
import io.kamax.matrix.client._MatrixClient;
import io.kamax.matrix.json.GsonUtil;

import java.util.Objects;

public class MatrixEndpoint extends GenericEndpoint {

    private _MatrixClient client;

    public MatrixEndpoint(MatrixBridgeUser user, String roomId, String callId) {
        super(user.getLocalId(), roomId, callId);
        this.client = user.getClient();
    }

    private void ifOpen(Runnable r) {
        if (Objects.isNull(client)) {
            return;
        }

        r.run();
    }

    private void ifOpenOrHangup(Runnable r) {
        try {
            ifOpen(r);
        } catch (RuntimeException ex) {
            close(CallHangupEvent.from(getCallId(), ex.getMessage()));
        }
    }

    void inject(String from, CallInviteEvent ev) {
        fireCallEvent(l -> l.onInvite(from, ev));
    }

    void inject(CallCandidatesEvent ev) {
        fireCallEvent(l -> l.onCandidates(ev));
    }

    void inject(CallAnswerEvent ev) {
        fireCallEvent(l -> l.onAnswer(ev));
    }

    void inject(CallHangupEvent ev) {
        fireCallEvent(l -> l.onHangup(ev));
    }

    @Override
    public void handle(String from, CallInviteEvent ev) {
        ifOpenOrHangup(() -> {
            ev.getOffer().setType("offer");
            client.getRoom(getChannelId()).sendEvent("m.call.invite", GsonUtil.makeObj(ev));
        });
    }

    @Override
    public void handle(CallCandidatesEvent ev) {
        // TODO
    }

    @Override
    public void handle(CallAnswerEvent ev) {
        ifOpenOrHangup(() -> {
            ev.getAnswer().setType("answer");
            client.getRoom(getChannelId()).sendEvent("m.call.answer", GsonUtil.makeObj(ev));
        });
    }

    @Override
    public void handle(CallHangupEvent evRemote) {
        close(evRemote);
    }

    private synchronized void close(CallHangupEvent ev) {
        ifOpen(() -> {
            try {
                client.getRoom(getChannelId()).sendEvent("m.call.hangup", GsonUtil.makeObj(CallHangupEvent.from(ev.getCallId(), ev.getReason())));
            } catch (RuntimeException e) {
                // TODO possibly report this as warning?
            } finally {
                client = null;
                fireEndpointEvent(EndpointListener::onClose);
            }
        });
    }

    public void close() {
        close(CallHangupEvent.from(getCallId(), null));
    }

}
