package org.svenehrke.triptychdemo.cross.purchasing;

/** Where a {@link SupplierOrder} stands. */
public enum SupplierOrderStatus {
	/** Sent to the supplier, not (fully) delivered yet; counts towards the DC's inventory position. */
	OPEN,
	/** Everything ordered has been delivered. */
	DELIVERED,
	/** Sending it to the supplier failed, so nothing will be delivered. */
	CANCELLED
}
