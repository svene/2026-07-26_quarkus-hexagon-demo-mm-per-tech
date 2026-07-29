package com.example.hexdemo.adapter.outbound.kafka;

import com.example.hexdemo.core.domain.Product;
import com.example.hexdemo.core.port.out.InventoryEventPublisherSPI;
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
