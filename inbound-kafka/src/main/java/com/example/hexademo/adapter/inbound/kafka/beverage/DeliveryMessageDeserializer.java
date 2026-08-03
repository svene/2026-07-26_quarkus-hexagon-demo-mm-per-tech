package com.example.hexademo.adapter.inbound.kafka.beverage;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawBeverageDelivery> {
    public DeliveryMessageDeserializer() { super(RawBeverageDelivery.class); }
}
