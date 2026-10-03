package org.svenehrke.triptychdemo.cross.location;

/**
 * One stock-holding place. The kinds are types, so an operation only one kind supports takes that type (e.g. a
 * cashpoint sale a {@link Store}, a request to the DC a {@link Replenished}). The set of locations is fixed, see
 * {@link Locations}.
 */
public sealed interface Location permits Warehouse, Replenished {

	String id();

	String name();
}
