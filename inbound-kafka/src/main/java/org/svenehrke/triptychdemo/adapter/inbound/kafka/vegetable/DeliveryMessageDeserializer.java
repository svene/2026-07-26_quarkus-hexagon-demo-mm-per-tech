package org.svenehrke.triptychdemo.adapter.inbound.kafka.vegetable;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawVegetableDelivery> {
    public DeliveryMessageDeserializer() { super(RawVegetableDelivery.class); }
}
