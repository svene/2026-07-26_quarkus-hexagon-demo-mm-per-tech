package org.svenehrke.triptychdemo.cross.purchasing;

/** Why a {@link SupplierOrder} was placed. */
public enum SupplierOrderOrigin {
	/** Head office ordered it on the admin page (or via the JSON API). */
	MANUAL,
	/** The DC's inventory position fell below its learned reorder point ({@code LearnedLevels}). */
	AUTOMATIC,
	/** The DC was seeded with the {@code Catalog}: it had no stock and no open order, after a start or a reset. */
	SEED
}
