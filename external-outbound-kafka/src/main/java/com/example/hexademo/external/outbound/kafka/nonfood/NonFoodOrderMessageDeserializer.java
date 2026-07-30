package com.example.hexademo.external.outbound.kafka.nonfood;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class NonFoodOrderMessageDeserializer extends ObjectMapperDeserializer<NonFoodOrderMessage> {
    public NonFoodOrderMessageDeserializer() {
        super(NonFoodOrderMessage.class);
    }
}
