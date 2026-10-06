package org.svenehrke.triptychdemo.cross.occupancy;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class OccupancyMessageDeserializer extends ObjectMapperDeserializer<OccupancyMessage> {
    public OccupancyMessageDeserializer() { super(OccupancyMessage.class); }
}
