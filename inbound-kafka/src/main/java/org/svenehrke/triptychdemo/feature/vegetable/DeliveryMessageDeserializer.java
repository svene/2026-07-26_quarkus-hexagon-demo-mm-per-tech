package org.svenehrke.triptychdemo.feature.vegetable;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawVegetableDelivery> {
    public DeliveryMessageDeserializer() { super(RawVegetableDelivery.class); }
}
