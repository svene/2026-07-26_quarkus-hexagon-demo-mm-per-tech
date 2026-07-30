package com.example.hexarcdemo.external.kafka.nonfood;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class NonFoodSupplierStub {

    @Inject
    @Channel("nonfood-deliveries-out")
    Emitter<DeliveryMessage> emitter;

    @Incoming("nonfood-orders")
    @Blocking
    public void processOrder(NonFoodOrderMessage order) {
        emitter.send(new DeliveryMessage(order.productName(), order.quantity()));
    }
}
