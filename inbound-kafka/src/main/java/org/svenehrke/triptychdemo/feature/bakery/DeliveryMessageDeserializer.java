package org.svenehrke.triptychdemo.feature.bakery;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawBakeryDelivery> {
    public DeliveryMessageDeserializer() { super(RawBakeryDelivery.class); }
}
