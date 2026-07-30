package com.example.hexademo.adapter.outbound.httpclient.vegetable;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "vegetables-supplier")
public interface VegetablesSupplierClient {

    @POST
    @Path("/vegetable-orders")
    @Consumes(MediaType.APPLICATION_JSON)
    void placeOrder(OrderRequest request);
}
