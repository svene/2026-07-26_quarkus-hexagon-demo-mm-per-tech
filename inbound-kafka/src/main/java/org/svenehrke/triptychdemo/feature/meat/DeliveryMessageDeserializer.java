package org.svenehrke.triptychdemo.feature.meat;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawMeatDelivery> {
    public DeliveryMessageDeserializer() { super(RawMeatDelivery.class); }
}
