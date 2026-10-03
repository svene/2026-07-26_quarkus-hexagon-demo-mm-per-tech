package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Replenished;

import java.time.Instant;

/** A stored request of {@code location} to the DC; {@code delivered} grows with every (partial) transfer. */
public record ReplenishmentRequest(long id, Replenished location, String productName, int requested, int delivered,
                                   RequestStatus status, Instant createdAt) {

	public int outstanding() {
		return status == RequestStatus.PENDING ? requested - delivered : 0;
	}
}
