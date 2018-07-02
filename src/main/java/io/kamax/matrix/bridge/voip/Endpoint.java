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

import io.kamax.matrix.bridge.voip.matrix.event.CallAnswerEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallCandidatesEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallHangupEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallInviteEvent;

public interface Endpoint {

    String getCallId();

    String getChannelId();

    String getUserId();

    void addListener(EndpointListener listener);

    void addListener(CallListener listener);

    void handle(String from, CallInviteEvent ev);

    void handle(CallCandidatesEvent ev);

    void handle(CallAnswerEvent ev);

    void handle(CallHangupEvent ev);

}
