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

package io.kamax.matrix.bridge.voip.remote.call;

import com.google.gson.JsonObject;

public class RpcException extends RuntimeException {

    private long code;
    private String message;
    private JsonObject raw;

    public RpcException(long code, String message, JsonObject raw) {
        this.code = code;
        this.message = message;
        this.raw = raw;
    }

    public long getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public JsonObject getRaw() {
        return raw;
    }

}
