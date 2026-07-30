package com.example.hexarcdemo.server;

import com.example.hexarcdemo.adapter.inbound.kafka.DeliveryMessage;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TestKafkaPurchasePublisher {

    @Inject @Channel("customer-purchases-out") Emitter<DeliveryMessage> emitter;

    public void publish(String productName, int quantity) {
        emitter.send(new DeliveryMessage(productName, quantity));
    }
}
