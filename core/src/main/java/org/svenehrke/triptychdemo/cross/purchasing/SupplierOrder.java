package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.products.ProductType;

import java.time.Instant;

/**
 * A recorded order of the DC from a supplier; {@code delivered} grows with every delivery of the product, which closes
 * open orders oldest first (deliveries carry no order id).
 */
public record SupplierOrder(long id, String productName, ProductType type, int quantity, int delivered,
                            SupplierOrderStatus status, SupplierOrderOrigin origin, Instant createdAt) {

	/** The {@code @Max} of the {@code XxxOrder}s: a larger automatic order is capped, the next check orders the rest. */
	public static final int MAX_QUANTITY = 2000;

	public int outstanding() {
		return status == SupplierOrderStatus.OPEN ? quantity - delivered : 0;
	}

	/** For the audit log, e.g. {@code supplier order 7 Apple 4/10 OPEN AUTOMATIC}. */
	public String describe() {
		return "supplier order " + id + " " + productName + " " + delivered + "/" + quantity + " " + status.name()
			+ " " + origin.name();
	}
}
