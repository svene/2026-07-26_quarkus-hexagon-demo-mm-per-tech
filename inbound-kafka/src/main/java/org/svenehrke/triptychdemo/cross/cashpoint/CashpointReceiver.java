package org.svenehrke.triptychdemo.cross.cashpoint;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseAPI;

import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.stream.Collectors;

@ApplicationScoped
public class CashpointReceiver {

    @Inject
    PurchaseAPI purchaseAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("cashpoint-purchases")
    @Blocking
    public void receive(PurchaseMessage message) {
        var parsedItems = message.items().stream()
            .map(i -> PurchaseItem.parse(i.productName(), i.quantity()))
            .toList();
        switch (Purchase.parse(parsedItems)) {
            case ParsedPurchase.Invalid invalid: {
                String items = message.items().stream()
                    .map(i -> i.productName() + " qty=" + i.quantity())
                    .collect(Collectors.joining(", "));
                auditLog.log("CashpointReceiver: PURCHASE_RECEIVED",
                    "INVALID: %s: %s".formatted(items, String.join(", ", invalid.messages())));
                break;
            }
            case Purchase purchase: {
                purchaseAPI.purchase(purchase);
                break;
            }
        }
    }
}
