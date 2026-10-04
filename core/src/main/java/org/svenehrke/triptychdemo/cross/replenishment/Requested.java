package org.svenehrke.triptychdemo.cross.replenishment;

import java.util.List;

/**
 * Result of {@link ReplenishmentRepositorySPI#request} and {@link ReplenishmentRepositorySPI#fulfil}: the request's
 * state afterwards, and every transfer the operation made - to it, and (for {@code request}) to older requests that
 * shared the stock with it.
 */
public record Requested(ReplenishmentRequest request, List<Transfer> transfers) {

	public Requested {
		transfers = List.copyOf(transfers);
	}
}
