package org.svenehrke.triptychdemo.cross.replenishment;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class ShipmentArrivalMessageDeserializer extends ObjectMapperDeserializer<ShipmentArrivalMessage> {
    public ShipmentArrivalMessageDeserializer() { super(ShipmentArrivalMessage.class); }
}
