package org.svenehrke.triptychdemo.cross;

import jakarta.ws.rs.core.Response;
import java.util.List;

/** Responses shared by the JSON API resources. A {@code 400} body is a plain JSON array of messages. */
public final class JsonResponses {

    private JsonResponses() {}

    public static Response badRequest(List<String> messages) {
        return Response.status(Response.Status.BAD_REQUEST).entity(messages).build();
    }
}
