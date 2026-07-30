package com.example.hexarcdemo.external.kafka.nonfood;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class NonFoodOrderMessageDeserializer extends ObjectMapperDeserializer<NonFoodOrderMessage> {
    public NonFoodOrderMessageDeserializer() {
        super(NonFoodOrderMessage.class);
    }
}
