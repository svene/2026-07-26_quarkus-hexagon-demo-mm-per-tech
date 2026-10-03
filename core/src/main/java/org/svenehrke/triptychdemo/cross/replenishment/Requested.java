package org.svenehrke.triptychdemo.cross.replenishment;

import java.util.List;

/**
 * Result of {@link ReplenishmentRepositorySPI#request}: the stored request (its state afterwards), and every transfer
 * the allocation that followed made - to it, and to older requests that shared the stock with it.
 */
public record Requested(ReplenishmentRequest request, List<Transfer> transfers) {

	public Requested {
		transfers = List.copyOf(transfers);
	}
}
