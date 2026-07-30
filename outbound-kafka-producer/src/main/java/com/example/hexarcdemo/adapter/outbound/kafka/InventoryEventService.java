package com.example.hexarcdemo.adapter.outbound.kafka;

import com.example.hexarcdemo.core.domain.Product;
import com.example.hexarcdemo.core.port.out.InventoryEventPublisherSPI;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

@ApplicationScoped
public class InventoryEventService implements InventoryEventPublisherSPI {

    @Inject
    @Channel("inventory-events")
    MutinyEmitter<InventoryEvent> emitter;

    @Override
    public void publishInventoryChanged(Product product) {
        emitter.sendAndAwait(new InventoryEvent(product.name(), product.type().name(), product.availableAmount()));
    }
}
