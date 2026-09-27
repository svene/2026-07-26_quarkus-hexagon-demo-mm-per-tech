package org.svenehrke.triptychdemo.feature.beverage;

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
public class BeveragesDeliveryReceiver {

    @Inject
    InventoryAPI inventoryAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("beverages-deliveries")
    @Blocking
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, abortOn = UnprocessableMessageException.class)
    public void receive(RawBeverageDelivery message) {
        rejectUnprocessable(message);
        // Mapping: RawBeverageDelivery -> BeverageDelivery:
        switch (BeverageDelivery.parse(message.productName(), message.quantity())) {
            case ParsedBeverageDelivery.Invalid invalid: {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("BeveragesDeliveryReceiver: BEVERAGE_DELIVERY_RECEIVED",
                    "INVALID: %s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                break;
            }
            case BeverageDelivery beverageDelivery: {
                auditLog.log("BeveragesDeliveryReceiver: BEVERAGE_DELIVERY_RECEIVED", beverageDelivery.productName() + " qty=" + beverageDelivery.quantity());
                inventoryAPI.updateBeverageAmount(beverageDelivery);
                auditLog.log("BeveragesDeliveryReceiver: BEVERAGE_INVENTORY_UPDATED", beverageDelivery.productName() + " +" + beverageDelivery.quantity());
                break;
            }
        }
    }

    // UnprocessableMessageException skips @Retry and sends the message to the dead-letter topic (see DeadLetterOrFailStop).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(RawBeverageDelivery message) {
        if (message == null) {
            throw new UnprocessableMessageException("null payload (tombstone)");
        }
    }
}
