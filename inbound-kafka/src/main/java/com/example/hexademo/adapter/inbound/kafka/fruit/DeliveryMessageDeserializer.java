package com.example.hexademo.adapter.inbound.kafka.fruit;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<DeliveryMessage> {
    public DeliveryMessageDeserializer() { super(DeliveryMessage.class); }
}
