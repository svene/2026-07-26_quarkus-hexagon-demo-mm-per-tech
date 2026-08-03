package com.example.hexademo.adapter.inbound.kafka.bakery;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawBakeryDelivery> {
    public DeliveryMessageDeserializer() { super(RawBakeryDelivery.class); }
}
