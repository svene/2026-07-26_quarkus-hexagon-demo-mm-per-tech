package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** The checkout systems' till control: the app opens or closes tills of a store (head office, demo). */
@RunOnVirtualThread
@Path("/cashpoint-stub/stores/{storeId}/tills")
public class CashpointTillsStub {

    @Inject
    CashpointStub cashpointStub;

    public record TillsRequest(int tills) {}

    /** 204; 400 outside 1..{@value CashpointStub#MAX_TILLS}; 404 for an unknown store. */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setTills(@PathParam("storeId") String storeId, TillsRequest request) {
        if (request == null || request.tills() < 1 || request.tills() > CashpointStub.MAX_TILLS) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return cashpointStub.requestTills(storeId, request.tills())
            ? Response.noContent().build()
            : Response.status(Response.Status.NOT_FOUND).build();
    }
}
