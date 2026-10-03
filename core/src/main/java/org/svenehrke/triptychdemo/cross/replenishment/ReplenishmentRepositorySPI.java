package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Replenished;

import java.util.List;
import java.util.Optional;

/**
 * Requests of the locations to the DC, and the stock transfers that serve them. Every method that moves stock
 * does so in one transaction together with the request it serves, so a transfer can never be lost or happen
 * twice. Rows are locked DC stock first, then requests, then the target location's stock, so concurrent calls
 * cannot deadlock (and a sale, which locks only its own location's rows, cannot either).
 * <p>
 * Requests of one product are served strictly oldest first; a request is served partially if the DC has less
 * than it needs, and stays {@link RequestStatus#PENDING} for the rest.
 */
public interface ReplenishmentRepositorySPI {

	/**
	 * Stores the request and serves it from DC stock right away, unless older requests of the same product are
	 * still pending (they come first). Empty if the DC has never carried the product.
	 */
	Optional<Transfer> request(StockRequest request);

	/** Serves the pending requests of {@code productName}, oldest first, as far as the DC stock goes. */
	List<Transfer> fulfilPending(String productName);

	/**
	 * Head office: serves this request with whatever the DC has, ahead of older ones. Empty if there is no such
	 * pending request.
	 */
	Optional<Transfer> fulfil(long requestId);

	/** Head office: cancels what is outstanding. Empty if there is no such pending request. */
	Optional<ReplenishmentRequest> reject(long requestId);

	/** Pending requests of all locations, oldest first. */
	List<ReplenishmentRequest> findPending();

	/** The most recent requests of {@code location}, newest first. */
	List<ReplenishmentRequest> findRecent(Replenished location, int limit);
}
