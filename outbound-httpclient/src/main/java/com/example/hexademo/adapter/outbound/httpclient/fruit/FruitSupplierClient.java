package com.example.hexademo.adapter.outbound.httpclient.fruit;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "fruit-supplier")
public interface FruitSupplierClient {

    @POST
    @Path("/orders")
    @Consumes(MediaType.APPLICATION_JSON)
    void placeOrder(OrderRequest request);
}
