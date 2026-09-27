package org.svenehrke.triptychdemo.cross.purchase;

public interface PurchaseAPI {

	/**
	 * Online purchase (shop, JSON API): all-or-nothing, rejected if any item is not in stock - also when
	 * several customers buy concurrently.
	 */
	PurchaseOutcome checkout(Purchase purchase);

	/**
	 * Physical-store sale (cashpoint): the goods are already gone, so it is recorded, never rejected. Selling
	 * more than is on record means the inventory was wrong; that is audit-logged as a stock discrepancy.
	 */
	void recordStoreSale(Purchase purchase);
}
