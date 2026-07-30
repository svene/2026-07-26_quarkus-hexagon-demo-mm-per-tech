package com.example.hexademo.adapter.outbound.httpclient.dairy;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "dairy-supplier")
public interface DairySupplierClient {

    @POST
    @Path("/dairy-orders")
    @Consumes(MediaType.APPLICATION_JSON)
    void placeOrder(OrderRequest request);
}
