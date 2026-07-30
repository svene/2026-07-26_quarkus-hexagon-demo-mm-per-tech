package com.example.hexademo.external.inbound.kafka.cashpoint;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/api/products")
@RegisterRestClient(configKey = "cashpoint-products")
public interface ProductsApiClient {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ProductInfo> listProducts();
}
