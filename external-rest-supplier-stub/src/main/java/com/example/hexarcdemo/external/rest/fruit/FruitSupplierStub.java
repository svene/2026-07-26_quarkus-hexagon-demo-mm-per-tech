package com.example.hexarcdemo.external.rest.fruit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
@Path("/orders")
public class FruitSupplierStub {

    @Inject
    @Channel("fruit-deliveries-out")
    Emitter<DeliveryMessage> emitter;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void placeOrder(OrderRequest request) {
        emitter.send(new DeliveryMessage(request.productName(), request.quantity()));
    }
}
