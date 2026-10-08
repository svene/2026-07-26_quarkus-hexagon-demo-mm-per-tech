package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.purchase.RecordedPurchase;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** {@code purchasedAt} is pre-formatted on the server as local time with seconds precision ({@code HH:mm:ss}). */
public record PurchaseVM(long id, int products, int units, String purchasedAt) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    static PurchaseVM of(RecordedPurchase purchase) {
        return new PurchaseVM(purchase.id(), purchase.products(), purchase.units(), TIME.format(purchase.purchasedAt()));
    }
}
