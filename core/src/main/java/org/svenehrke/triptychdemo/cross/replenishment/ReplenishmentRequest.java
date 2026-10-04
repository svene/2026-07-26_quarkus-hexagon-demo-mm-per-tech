package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Replenished;

import java.time.Instant;

/**
 * A stored request of {@code location} to the DC; {@code shipped} grows with every (partial) transfer. What is shipped
 * may still be in transit (see {@link Shipment}).
 */
public record ReplenishmentRequest(long id, Replenished location, String productName, int requested, int shipped,
                                   RequestStatus status, RequestOrigin origin, Instant createdAt) {

	public int outstanding() {
		return status == RequestStatus.PENDING ? requested - shipped : 0;
	}
}
