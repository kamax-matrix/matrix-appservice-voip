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

import io.kamax.matrix.bridge.voip.*;
import io.kamax.matrix.client._MatrixClient;
import io.kamax.matrix.json.GsonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MatrixEndpoint {

    private _MatrixClient client;
    private String roomId;
    private String callId;
    private String remoteId;

    private List<CallListener> listeners = new ArrayList<>();

    public MatrixEndpoint(MatrixBridgeUser user, String roomId, String callId) {
        this.client = user.getClient();
        this.remoteId = user.getRemoteId();
        this.roomId = roomId;
        this.callId = callId;
    }

    void inject(CallInviteEvent ev) {
        listeners.forEach(l -> l.onInvite(remoteId, ev));
    }

    void inject(CallCandidatesEvent ev) {
        listeners.forEach(l -> l.onCandidates(ev));
    }

    void inject(CallAnswerEvent ev) {
        listeners.forEach(l -> l.onAnswer(ev));
    }

    void inject(CallHangupEvent ev) {
        listeners.forEach(l -> l.onHangup(ev));
    }

    public void addListener(CallListener listener) {
        listeners.add(listener);
    }

    public void handle(CallInviteEvent ev) {
        ev.getOffer().setType("offer");
        client.getRoom(roomId).sendEvent("m.call.invite", GsonUtil.makeObj(ev));
    }

    public void handle(CallAnswerEvent ev) {
        ev.getAnswer().setType("answer");
        client.getRoom(roomId).sendEvent("m.call.answer", GsonUtil.makeObj(ev));
    }

    public synchronized void handle(CallHangupEvent evRemote) {
        if (Objects.isNull(client)) {
            // we are already done
            return;
        }
        CallHangupEvent ev = new CallHangupEvent();
        ev.setCallId(evRemote.getCallId());
        ev.setVersion(0L);
        ev.setReason(evRemote.getReason());
        client.getRoom(roomId).sendEvent("m.call.hangup", GsonUtil.makeObj(ev));

        client = null;
    }

}
