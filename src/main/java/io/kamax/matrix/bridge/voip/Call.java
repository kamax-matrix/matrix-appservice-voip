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
import io.kamax.matrix.bridge.voip.matrix.event.CallAnswerEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallCandidatesEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallHangupEvent;
import io.kamax.matrix.bridge.voip.matrix.event.CallInviteEvent;
import io.kamax.matrix.bridge.voip.remote.RemoteEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class Call {

    private final Logger log = LoggerFactory.getLogger(Call.class);

    private String id;
    private MatrixEndpoint local;
    private RemoteEndpoint remote;

    public Call(String id, MatrixEndpoint local, RemoteEndpoint remote) {
        this.id = id;
        this.local = local;
        this.remote = remote;

        this.local.addListener(new CallListener() {

            @Override
            public void onInvite(String from, CallInviteEvent ev) {
                log.info("Call {}: Matrix: invite from {}", id, from);
                remote.handle(from, ev);
            }

            @Override
            public void onSdp(CallSdpEvent ev) {
                // This should never happen
                log.warn("We got Call SDP event from Matrix!");
            }

            @Override
            public void onCandidates(CallCandidatesEvent ev) {
                log.info("Call {}: Matrix: candidates", id);
                remote.handle(ev);
            }

            @Override
            public void onAnswer(CallAnswerEvent ev) {
                log.info("Call {}: Matrix: answer", id);
                remote.handle(ev);
            }

            @Override
            public void onHangup(CallHangupEvent ev) {
                log.info("Call {}: Matrix: hangup", id);
                remote.handle(ev);
                terminate(ev.getReason());
            }
        });
        this.local.addListener(() -> {
            log.info("Call {}: Matrix: close", id);
            terminate();
        });

        this.remote.addListener(new CallListener() {

            @Override
            public void onInvite(String from, CallInviteEvent ev) {
                log.info("Call {}: Remote: invite from {}", id, from);
                local.handle(from, ev);
            }

            @Override
            public void onSdp(CallSdpEvent ev) {
                // This should never happen
                log.warn("We got Call SDP event from Remote!");
            }

            @Override
            public void onCandidates(CallCandidatesEvent ev) {
                log.info("Call {}: Remote: candidates", id);
            }

            @Override
            public void onAnswer(CallAnswerEvent ev) {
                log.info("Call {}: Remote: answer", id);
                local.handle(ev);
            }

            @Override
            public void onHangup(CallHangupEvent ev) {
                log.info("Call {}: Remote: hangup", id);
                local.handle(ev);
                terminate(ev.getReason());
            }
        });
        this.remote.addListener(() -> {
            log.info("Call {}: Remote: close", id);
            terminate();
        });
    }

    public synchronized void terminate(String reason) {
        if (Objects.isNull(local) && Objects.isNull(remote)) {
            return;
        }

        log.info("Call {}: terminating", id);
        if (Objects.nonNull(local)) {
            local.close();
            local = null;
        }

        if (Objects.nonNull(remote)) {
            remote.close();
            remote = null;
        }
    }

    public void terminate() {
        terminate(null);
    }

}
