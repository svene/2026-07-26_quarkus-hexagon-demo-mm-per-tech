package org.svenehrke.triptychdemo.cross.occupancy;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/cashpoint-stub/stores")
@RegisterRestClient(configKey = "checkout-system")
public interface CheckoutSystemClient {

    /** Throws a {@code WebApplicationException} on a 4xx/5xx answer. */
    @PUT
    @Path("/{storeId}/tills")
    @Consumes(MediaType.APPLICATION_JSON)
    void setTills(@PathParam("storeId") String storeId, TillsRequest request);

    record TillsRequest(int tills) {}
}
