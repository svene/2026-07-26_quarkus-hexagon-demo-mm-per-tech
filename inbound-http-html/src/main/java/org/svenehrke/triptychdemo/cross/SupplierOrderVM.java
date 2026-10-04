package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrder;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** {@code createdAt} is pre-formatted like {@link RequestVM}'s. */
public record SupplierOrderVM(long id, String productName, String type, int quantity, int delivered, String origin,
                              String createdAt) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    static SupplierOrderVM of(SupplierOrder order) {
        return new SupplierOrderVM(order.id(), order.productName(), order.type().name(), order.quantity(),
            order.delivered(), order.origin().name(), TIME.format(order.createdAt()));
    }
}
