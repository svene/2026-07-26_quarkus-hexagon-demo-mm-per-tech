package org.svenehrke.triptychdemo.cross.cashpoint;

import org.svenehrke.triptychdemo.cross.purchase.PurchaseAPI;

import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class CashpointReceiver {

    @Inject
    PurchaseAPI purchaseAPI;

    @Incoming("cashpoint-purchases")
    @Blocking
    public void receive(PurchaseMessage message) {
        var items = message.items().stream()
            .map(i -> new PurchaseItem(i.productName(), i.quantity()))
            .toList();
        purchaseAPI.purchase(items);
    }
}
