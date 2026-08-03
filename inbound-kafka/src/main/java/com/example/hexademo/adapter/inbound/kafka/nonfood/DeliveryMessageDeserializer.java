package com.example.hexademo.adapter.inbound.kafka.nonfood;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawNonFoodDelivery> {
    public DeliveryMessageDeserializer() { super(RawNonFoodDelivery.class); }
}
