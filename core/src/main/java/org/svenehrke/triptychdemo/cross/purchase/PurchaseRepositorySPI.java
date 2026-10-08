package org.svenehrke.triptychdemo.cross.purchase;

import org.svenehrke.triptychdemo.cross.location.Replenished;

import java.time.Instant;
import java.util.List;

public interface PurchaseRepositorySPI {

	/**
	 * Stores the completed {@code purchase} of {@code location} (its number of distinct products and its units), and
	 * drops the location's purchases made before {@code keepSince}.
	 */
	void append(Replenished location, Purchase purchase, Instant purchasedAt, Instant keepSince);

	/** The most recent purchases of {@code location}, newest first. */
	List<RecordedPurchase> findRecent(Replenished location, int limit);
}
