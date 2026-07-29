package com.example.hexdemo.adapter.outbound.webservice;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "beverage-supplier")
public interface BeverageSupplierClient {

    @POST
    @Path("/orders")
    @Consumes(MediaType.APPLICATION_JSON)
    void placeOrder(BeverageOrderRequest request);
}
