package org.svenehrke.triptychdemo.feature.dairy;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeliveryMessageDeserializer extends ObjectMapperDeserializer<RawDairyDelivery> {
    public DeliveryMessageDeserializer() { super(RawDairyDelivery.class); }
}
