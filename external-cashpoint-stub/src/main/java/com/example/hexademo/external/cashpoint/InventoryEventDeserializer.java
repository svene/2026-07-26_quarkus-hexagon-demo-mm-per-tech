package com.example.hexademo.external.cashpoint;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class InventoryEventDeserializer extends ObjectMapperDeserializer<InventoryEvent> {
    public InventoryEventDeserializer() { super(InventoryEvent.class); }
}
