package com.example.hexademo.adapter.inbound.kafka.cashpoint;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class PurchaseMessageDeserializer extends ObjectMapperDeserializer<PurchaseMessage> {
    public PurchaseMessageDeserializer() { super(PurchaseMessage.class); }
}
