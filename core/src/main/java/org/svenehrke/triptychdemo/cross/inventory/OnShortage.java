package org.svenehrke.triptychdemo.cross.inventory;

/** What {@link InventoryRepositorySPI#deductAll} does when a product has less stock than requested. */
public enum OnShortage {
	/** Deduct nothing at all (online checkout: don't sell what isn't there). */
	REJECT,
	/** Deduct everything, stock never below 0 (physical store: the goods are already gone). */
	CAP_AT_ZERO
}
