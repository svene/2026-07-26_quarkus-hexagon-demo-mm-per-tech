package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/api/locations")
@RegisterRestClient(configKey = "cashpoint-products")
public interface ProductsApiClient {

    @GET
    @Path("/{id}/products")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProductInfo> listProducts(@PathParam("id") String locationId);
}
