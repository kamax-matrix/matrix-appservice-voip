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

import java.util.Objects;

public class CallInviteEvent extends CallEvent {

    public static CallInviteEvent get(String callId, String sdp, long lifetime) {
        Offer o = new Offer();
        o.setType("invite");
        o.setSdp(sdp);
        CallInviteEvent ev = new CallInviteEvent();
        ev.setCallId(callId);
        ev.setOffer(o);
        ev.setLifetime(lifetime);
        return ev;
    }

    public static class Offer {

        private String type;
        private String sdp;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSdp() {
            return sdp;
        }

        public void setSdp(String sdp) {
            this.sdp = sdp;
        }

        public boolean isValid() {
            return Objects.nonNull(type) && Objects.nonNull(sdp);
        }

    }

    private Offer offer;
    private Long lifetime;

    public Offer getOffer() {
        return offer;
    }

    public void setOffer(Offer offer) {
        this.offer = offer;
    }

    public Long getLifetime() {
        return lifetime;
    }

    public void setLifetime(long lifetime) {
        this.lifetime = lifetime;
    }

    public boolean isValid() {
        return super.isValid() &&
                Objects.nonNull(lifetime) &&
                Objects.nonNull(offer) &&
                offer.isValid();
    }

}
