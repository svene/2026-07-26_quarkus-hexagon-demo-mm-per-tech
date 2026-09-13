package org.svenehrke.triptychdemo.feature.nonfood;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawNonFoodDelivery> {
    public DeliveryMessageDeserializer() { super(RawNonFoodDelivery.class); }
}
