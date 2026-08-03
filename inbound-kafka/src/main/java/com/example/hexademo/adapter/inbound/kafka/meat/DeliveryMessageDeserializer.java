package com.example.hexademo.adapter.inbound.kafka.meat;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawMeatDelivery> {
    public DeliveryMessageDeserializer() { super(RawMeatDelivery.class); }
}
