package org.svenehrke.triptychdemo.adapter.inbound.kafka.fruit;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawFruitDelivery> {
    public DeliveryMessageDeserializer() { super(RawFruitDelivery.class); }
}
