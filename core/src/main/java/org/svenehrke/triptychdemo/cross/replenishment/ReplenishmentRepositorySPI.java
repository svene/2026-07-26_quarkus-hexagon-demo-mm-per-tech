package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Replenished;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Requests of the locations to the DC, and the stock transfers that serve them. Every method that moves stock
 * does so in one transaction together with the requests it serves, so a transfer can never be lost or happen
 * twice. Rows are locked DC stock first, then requests, then the target location's stock, so concurrent calls
 * cannot deadlock (and a sale, which locks only its own location's rows, cannot either).
 * <p>
 * A transfer takes the stock off the DC and records a {@link Shipment} in transit; the location's stock grows only
 * when the shipment arrives ({@link #receiveShipment}).
 * <p>
 * DC stock is handed out by {@link #allocate}: shared among all pending requests of the product, see {@link FairShare}.
 * A request that gets less than it needs stays {@link RequestStatus#PENDING} for the rest.
 */
public interface ReplenishmentRepositorySPI {

	/** Stores the request and allocates the DC stock of its product. Empty if the DC has never carried the product. */
	Optional<Requested> request(StockRequest request);

	/**
	 * Automatic replenishment: if the inventory position of the product at {@code location} (available + in transit +
	 * outstanding requests) is below its learned reorder point, stores a request up to the order-up-to level (at most
	 * {@link StockRequest#MAX_QUANTITY}) - without serving it, so the requests of several locations can be collected
	 * before {@link #allocate} shares the stock among them. Checking and creating happen in one transaction with the
	 * rows locked, so concurrent sales cannot both order. Empty if nothing was ordered, or the DC has never carried
	 * the product.
	 */
	Optional<ReplenishmentRequest> requestIfLow(Replenished location, String productName);

	/**
	 * Shares the DC stock of {@code productName} among its pending requests ({@link FairShare}). Returns a transfer per
	 * request that got something.
	 */
	List<Transfer> allocate(String productName);

	/**
	 * Head office: serves this request with whatever the DC has, ahead of the others (no transfer if the DC has
	 * nothing). Empty if there is no such pending request.
	 */
	Optional<Requested> fulfil(long requestId);

	/** Head office: cancels what is outstanding. Empty if there is no such pending request. */
	Optional<ReplenishmentRequest> reject(long requestId);

	/**
	 * A shipment arrived: adds it to its location's stock. Empty if there is no such shipment in transit - unknown, or
	 * arrived already (a repeated arrival message), so booking an arrival twice is harmless.
	 */
	Optional<Shipment> receiveShipment(long shipmentId);

	/** Shipments still in transit that were dispatched before {@code dispatchedBefore}, oldest first. */
	List<Shipment> findInTransit(Instant dispatchedBefore);

	/** Pending requests of all locations, oldest first. */
	List<ReplenishmentRequest> findPending();

	/** The most recent requests of {@code location}, newest first. */
	List<ReplenishmentRequest> findRecent(Replenished location, int limit);
}
