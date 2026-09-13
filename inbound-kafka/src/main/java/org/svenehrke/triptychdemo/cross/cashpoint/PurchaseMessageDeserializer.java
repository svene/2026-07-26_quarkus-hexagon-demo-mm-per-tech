package org.svenehrke.triptychdemo.cross.cashpoint;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class PurchaseMessageDeserializer extends ObjectMapperDeserializer<PurchaseMessage> {
    public PurchaseMessageDeserializer() { super(PurchaseMessage.class); }
}
