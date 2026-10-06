package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.svenehrke.triptychdemo.cross.kafka.UnprocessableMessageException;

import java.time.temporal.ChronoUnit;

/**
 * The carrier reports that a shipment from the DC arrived at its location. The same arrival may come more than once
 * (a redelivery after a rebalance, a redispatched shipment); the Handler books it only once.
 */
@ApplicationScoped
public class ShipmentArrivalReceiver {

    @Inject
    ReplenishmentHandler replenishmentHandler;
    @Inject
    AuditLogHandler auditLog;

    @Incoming("shipment-arrivals")
    @RunOnVirtualThread
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, abortOn = UnprocessableMessageException.class)
    public void receive(ShipmentArrivalMessage message) {
        auditLog.log("ShipmentArrivalReceiver: SHIPMENT_ARRIVAL_RECEIVED",
            message == null ? "null payload (tombstone)" : "shipment " + message.shipmentId());
        rejectUnprocessable(message);
        replenishmentHandler.receiveShipment(message.shipmentId());
    }

    // UnprocessableMessageException skips @Retry and sends the message to the dead-letter topic (see DeadLetterOrFailStop).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(ShipmentArrivalMessage message) {
        if (message == null) {
            throw new UnprocessableMessageException("null payload (tombstone)");
        }
        if (message.shipmentId() == null) {
            throw new UnprocessableMessageException("shipmentId is required");
        }
    }
}
