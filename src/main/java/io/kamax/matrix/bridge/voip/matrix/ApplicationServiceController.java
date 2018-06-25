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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kamax.matrix.MatrixErrorInfo;
import io.kamax.matrix.ThreePid;
import io.kamax.matrix.event._MatrixEvent;
import io.kamax.matrix.json.GsonUtil;
import io.kamax.matrix.json.MatrixJsonEventFactory;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

@RestController
public class ApplicationServiceController {

    private Logger log = LoggerFactory.getLogger(ApplicationServiceController.class);

    private MatrixManager as;

    @Autowired
    public ApplicationServiceController(MatrixManager as) {
        this.as = as;
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler({InvalidIdException.class, IllegalArgumentException.class})
    @ResponseBody
    MatrixErrorInfo handleBadRequest(HttpServletRequest request, MatrixException e) {
        log.error("Error when processing {} {}", request.getMethod(), request.getServletPath(), e);

        return new MatrixErrorInfo(e.getErrorCode());
    }

    @ResponseStatus(value = HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(NoHomeserverTokenException.class)
    @ResponseBody
    MatrixErrorInfo handleUnauthorized(MatrixException e) {
        return new MatrixErrorInfo(e.getErrorCode());
    }

    @ResponseStatus(value = HttpStatus.FORBIDDEN)
    @ExceptionHandler(InvalidHomeserverTokenException.class)
    @ResponseBody
    MatrixErrorInfo handleForbidden(MatrixException e) {
        return new MatrixErrorInfo(e.getErrorCode());
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    @ExceptionHandler({RoomNotFoundException.class, UserNotFoundException.class})
    @ResponseBody
    MatrixErrorInfo handleNotFound(MatrixException e) {
        return new MatrixErrorInfo(e.getErrorCode());
    }

    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Throwable.class)
    @ResponseBody
    MatrixErrorInfo handleGeneric(HttpServletRequest request, Throwable t) {
        log.error("Error when processing {} {}", request.getMethod(), request.getServletPath(), t);

        return new MatrixErrorInfo(t);
    }

    @RequestMapping(value = "/rooms/{alias:.+}", method = GET)
    public Object getRoom(
            @RequestParam(name = "access_token", required = false) String token,
            @PathVariable String alias) {
        log.info("Room {} was requested by HS", alias);

        as.forHome(token).queryRoom(alias);
        return "{}";
    }

    @RequestMapping(value = "/users/{id:.+}", method = GET)
    public Object getUser(
            @RequestParam(name = "access_token", required = false) String token,
            @PathVariable String id) {
        log.info("User {} was requested by HS", id);

        as.forHome(token).queryUser(id);
        return "{}";
    }

    @RequestMapping(value = "/transactions/{txnId:.+}", method = PUT)
    public Object getTransaction(
            HttpServletRequest request,
            @RequestParam(name = "access_token", required = false) String token,
            @PathVariable String txnId) throws IOException {
        log.info("Processing {}", request.getServletPath());

        JsonObject body = GsonUtil.parseObj(IOUtils.toString(request.getInputStream(), request.getCharacterEncoding()));
        JsonArray eventsJson = GsonUtil.getArray(body, "events");

        List<_MatrixEvent> events = new ArrayList<>();
        for (JsonElement event : eventsJson) {
            events.add(MatrixJsonEventFactory.get(event.getAsJsonObject()));
        }

        Transaction transaction = new Transaction();
        transaction.setId(txnId);
        transaction.setEvents(events);

        as.forHome(token).process(transaction);
        return "{}";
    }

    @RequestMapping(value = "/_matrix/identity/api/v1/lookup", method = GET)
    public String lookup(@RequestParam String medium, @RequestParam String address) {
        ThreePid threePid = new ThreePid(medium, address);

        return GsonUtil.get().toJson(as.forIdentity().getMatrixId(threePid)
                .map(ThreePidMappingAnswer::get)
                .orElseGet(ThreePidMappingAnswer::new));
    }

}
