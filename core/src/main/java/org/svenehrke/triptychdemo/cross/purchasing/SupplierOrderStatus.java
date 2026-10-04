package org.svenehrke.triptychdemo.cross.purchasing;

import java.util.List;

/** Where a {@link SupplierOrder} stands. */
public sealed interface SupplierOrderStatus {

	Open OPEN = new Open();
	Delivered DELIVERED = new Delivered();
	Cancelled CANCELLED = new Cancelled();

	/** Sent to the supplier, not (fully) delivered yet; counts towards the DC's inventory position. */
	record Open() implements SupplierOrderStatus {
		@Override public String name() { return "OPEN"; }
	}

	/** Everything ordered has been delivered. */
	record Delivered() implements SupplierOrderStatus {
		@Override public String name() { return "DELIVERED"; }
	}

	/** Sending it to the supplier failed, so nothing will be delivered. */
	record Cancelled() implements SupplierOrderStatus {
		@Override public String name() { return "CANCELLED"; }
	}

	/** Stable, for storing and showing it. */
	String name();

	/** The inverse of {@link #name()}. */
	static SupplierOrderStatus of(String name) {
		return List.of(OPEN, DELIVERED, CANCELLED).stream().filter(s -> s.name().equals(name)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("unknown supplier order status: " + name));
	}
}
