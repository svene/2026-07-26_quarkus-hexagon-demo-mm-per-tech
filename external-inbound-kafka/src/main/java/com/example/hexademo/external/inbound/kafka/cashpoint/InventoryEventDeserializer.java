package com.example.hexademo.external.inbound.kafka.cashpoint;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class InventoryEventDeserializer extends ObjectMapperDeserializer<InventoryEvent> {
    public InventoryEventDeserializer() { super(InventoryEvent.class); }
}
