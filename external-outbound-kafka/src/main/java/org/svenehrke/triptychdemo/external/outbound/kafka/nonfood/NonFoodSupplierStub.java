package org.svenehrke.triptychdemo.external.outbound.kafka.nonfood;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.svenehrke.triptychdemo.external.outbound.kafka.LeadTime;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class NonFoodSupplierStub {

    @Inject
    @Channel("nonfood-deliveries-out")
    Emitter<DeliveryMessage> emitter;

    @Inject
    LeadTime leadTime;

    @Incoming("nonfood-orders")
    @Blocking
    public void processOrder(NonFoodOrderMessage order) {
        leadTime.deliverLater(() -> emitter.send(new DeliveryMessage(order.productName(), order.quantity())));
    }
}
