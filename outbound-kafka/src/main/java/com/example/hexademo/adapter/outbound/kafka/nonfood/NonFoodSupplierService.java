package com.example.hexademo.adapter.outbound.kafka.nonfood;

import com.example.hexademo.core.spi.NonFoodSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class NonFoodSupplierService implements NonFoodSupplierSPI {

    @Inject
    @Channel("nonfood-orders-out")
    Emitter<NonFoodOrderMessage> emitter;

    @Override
    public void placeOrder(String productName, int quantity) {
        emitter.send(new NonFoodOrderMessage(productName, quantity));
    }
}
