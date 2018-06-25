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

import io.kamax.matrix.bridge.voip.CallHangupEvent;
import io.kamax.matrix.bridge.voip.CallInviteEvent;
import io.kamax.matrix.bridge.voip.CallListener;
import io.kamax.matrix.bridge.voip.remote.call.FreeswitchEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RemoteEndpoint {

    private String remoteId;
    private FreeswitchEndpoint voip;

    private List<CallListener> listeners = new ArrayList<>();

    public RemoteEndpoint(String remoteId, FreeswitchEndpoint voip) {
        this.remoteId = remoteId;
        this.voip = voip;
    }

    void inject(String from, CallInviteEvent ev) {
        listeners.forEach(l -> l.onInvite(from, ev));
    }

    void inject(CallHangupEvent ev) {
        listeners.forEach(l -> l.onHangup(ev));
        close();
    }

    public String getId() {
        return remoteId;
    }

    public void addListener(CallListener listener) {
        listeners.add(listener);
    }

    public void handle(String destination, CallInviteEvent ev) {
        voip.handle(destination, ev);
    }

    public void handle(CallHangupEvent ev) {
        voip.handle(ev);
    }

    public boolean isClosed() {
        return Objects.isNull(voip);
    }

    public void close() {
        if (isClosed()) {
            return;
        }

        voip = null;
        listeners.forEach(CallListener::onClose);
    }

}
