package org.svenehrke.triptychdemo.external.outbound.kafka.carrier;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class ShipmentMessageDeserializer extends ObjectMapperDeserializer<ShipmentMessage> {
    public ShipmentMessageDeserializer() {
        super(ShipmentMessage.class);
    }
}
