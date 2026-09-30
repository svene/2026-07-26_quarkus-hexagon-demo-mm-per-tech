package org.svenehrke.triptychdemo.cross.cashpoint;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseHandler;

import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.svenehrke.triptychdemo.cross.kafka.UnprocessableMessageException;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class CashpointReceiver {

    @Inject
    PurchaseHandler purchaseHandler;
    @Inject
    AuditLogHandler auditLog;

    @Incoming("cashpoint-purchases")
    @Blocking
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, abortOn = UnprocessableMessageException.class)
    public void receive(PurchaseMessage message) {
        logReceived(message);
        validated(message).ifPresent(purchaseHandler::recordStoreSale);
    }

    private void logReceived(PurchaseMessage message) {
        auditLog.log("CashpointReceiver: PURCHASE_RECEIVED",
            message == null ? "null payload (tombstone)" : itemsOf(message));
    }

    // Empty if the message is invalid (audit-logged, then skipped).
    private Optional<Purchase> validated(PurchaseMessage message) {
        rejectUnprocessable(message);
        var parsedItems = message.items().stream()
            .map(i -> PurchaseItem.parse(i.productName(), i.quantity()))
            .toList();
        return switch (Purchase.parse(parsedItems)) {
            case ParsedPurchase.Invalid invalid -> {
                auditLog.log("CashpointReceiver: INVALID",
                    "%s: %s".formatted(itemsOf(message), String.join(", ", invalid.messages())));
                yield Optional.empty();
            }
            case Purchase purchase -> Optional.of(purchase);
        };
    }

    // Tolerates the structural problems rejectUnprocessable() rejects, since it also runs before that check.
    private static String itemsOf(PurchaseMessage message) {
        if (message.items() == null) {
            return "items: null";
        }
        return message.items().stream()
            .map(i -> i == null ? "null" : i.productName() + " qty=" + i.quantity())
            .collect(Collectors.joining(", "));
    }

    // UnprocessableMessageException skips @Retry and sends the message to the dead-letter topic (see DeadLetterOrFailStop).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(PurchaseMessage message) {
        if (message == null) {
            throw new UnprocessableMessageException("null payload (tombstone)");
        }
        if (message.items() == null) {
            throw new UnprocessableMessageException("items is required");
        }
        if (message.items().contains(null)) {
            throw new UnprocessableMessageException("items must not contain null entries");
        }
    }
}
