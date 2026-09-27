package org.svenehrke.triptychdemo.feature.bakery;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.svenehrke.triptychdemo.cross.kafka.UnprocessableMessageException;

import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class BakeryDeliveryReceiver {

    @Inject
    InventoryAPI inventoryAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("bakery-deliveries")
    @Blocking
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, abortOn = UnprocessableMessageException.class)
    public void receive(RawBakeryDelivery message) {
        rejectUnprocessable(message);
        // Mapping: RawBakeryDelivery -> BakeryDelivery:
        switch (BakeryDelivery.parse(message.productName(), message.quantity())) {
            case ParsedBakeryDelivery.Invalid invalid: {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("BakeryDeliveryReceiver: BAKERY_DELIVERY_RECEIVED",
                    "INVALID: %s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                break;
            }
            case BakeryDelivery bakeryDelivery: {
                auditLog.log("BakeryDeliveryReceiver: BAKERY_DELIVERY_RECEIVED", bakeryDelivery.productName() + " qty=" + bakeryDelivery.quantity());
                inventoryAPI.updateBakeryAmount(bakeryDelivery);
                auditLog.log("BakeryDeliveryReceiver: BAKERY_INVENTORY_UPDATED", bakeryDelivery.productName() + " +" + bakeryDelivery.quantity());
                break;
            }
        }
    }

    // UnprocessableMessageException skips @Retry and sends the message to the dead-letter topic (see DeadLetterOrFailStop).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(RawBakeryDelivery message) {
        if (message == null) {
            throw new UnprocessableMessageException("null payload (tombstone)");
        }
    }
}
