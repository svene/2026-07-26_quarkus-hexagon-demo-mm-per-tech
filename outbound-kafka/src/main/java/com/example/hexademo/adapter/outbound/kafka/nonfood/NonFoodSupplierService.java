package com.example.hexademo.adapter.outbound.kafka.nonfood;

import com.example.hexademo.core.application.SPIs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class NonFoodSupplierService implements SPIs.NonFoodSupplierSPI {

    @Inject
    @Channel("nonfood-orders-out")
    Emitter<NonFoodOrderMessage> emitter;

    @Override
    public void placeOrder(String productName, int quantity) {
        emitter.send(new NonFoodOrderMessage(productName, quantity));
    }
}
