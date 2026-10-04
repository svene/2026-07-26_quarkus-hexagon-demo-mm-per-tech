package org.svenehrke.triptychdemo.cross.purchasing;

/** Why a {@link SupplierOrder} was placed. */
public enum SupplierOrderOrigin {
	/** Head office ordered it on the admin page (or via the JSON API). */
	MANUAL,
	/** The DC's inventory position fell below its learned reorder point ({@code LearnedLevels}). */
	AUTOMATIC
}
