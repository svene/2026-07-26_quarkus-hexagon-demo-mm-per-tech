package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.adapter.inbound.kafka.cashpoint.PurchaseMessage;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TestCashpointPublisher {

    @Inject @Channel("testing-cashpoint-purchases-out") Emitter<PurchaseMessage> emitter;

    public void publish(PurchaseMessage message) {
        emitter.send(message);
    }
}
