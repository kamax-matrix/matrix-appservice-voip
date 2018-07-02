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

import io.kamax.matrix.bridge.voip.matrix.event.CallHangupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class GenericEndpoint implements Endpoint {

    private String callId;
    private String channelId;
    private String userId;

    private List<CallListener> cListeners = new ArrayList<>();
    private List<EndpointListener> eListeners = new ArrayList<>();

    protected GenericEndpoint(String userId, String channelId, String callId) {
        this.callId = callId;
        this.channelId = channelId;
        this.userId = userId;
    }

    private <T> void fireEvent(List<T> listeners, Consumer<T> c) {
        listeners.forEach(l -> {
            try {
                c.accept(l);
            } catch (RuntimeException e) {
                // FIXME show error
            }
        });
    }

    protected void fireCallEvent(Consumer<CallListener> c) {
        fireEvent(cListeners, c);
    }

    protected void fireEndpointEvent(Consumer<EndpointListener> c) {
        fireEvent(eListeners, c);
    }

    @Override
    public String getCallId() {
        return callId;
    }

    @Override
    public String getChannelId() {
        return channelId;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public void addListener(EndpointListener listener) {
        eListeners.add(listener);
    }

    @Override
    public void addListener(CallListener listener) {
        cListeners.add(listener);
    }

    protected void fireHangupEvent(String reason) {
        fireEvent(cListeners, l -> l.onHangup(CallHangupEvent.from(callId, reason)));
    }

}
