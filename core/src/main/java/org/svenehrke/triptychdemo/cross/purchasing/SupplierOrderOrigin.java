package org.svenehrke.triptychdemo.cross.purchasing;

import java.util.List;

/** Why a {@link SupplierOrder} was placed. */
public sealed interface SupplierOrderOrigin {

	Manual MANUAL = new Manual();
	Automatic AUTOMATIC = new Automatic();

	/** Head office ordered it on the admin page (or via the JSON API). */
	record Manual() implements SupplierOrderOrigin {
		@Override public String name() { return "MANUAL"; }
	}

	/** The DC's inventory position fell below its learned reorder point ({@code LearnedLevels}). */
	record Automatic() implements SupplierOrderOrigin {
		@Override public String name() { return "AUTOMATIC"; }
	}

	/** Stable, for storing and showing it. */
	String name();

	/** The inverse of {@link #name()}. */
	static SupplierOrderOrigin of(String name) {
		return List.of(MANUAL, AUTOMATIC).stream().filter(o -> o.name().equals(name)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("unknown supplier order origin: " + name));
	}
}
