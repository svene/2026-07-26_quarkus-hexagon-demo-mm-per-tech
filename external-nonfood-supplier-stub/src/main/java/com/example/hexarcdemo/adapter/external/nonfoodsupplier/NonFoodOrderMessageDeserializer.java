package com.example.hexarcdemo.adapter.external.nonfoodsupplier;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class NonFoodOrderMessageDeserializer extends ObjectMapperDeserializer<NonFoodOrderMessage> {
    public NonFoodOrderMessageDeserializer() {
        super(NonFoodOrderMessage.class);
    }
}
