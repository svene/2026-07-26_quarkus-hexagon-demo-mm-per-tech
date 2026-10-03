package org.svenehrke.triptychdemo.cross.location;

import java.util.List;
import java.util.Optional;

/** The fixed set of locations: a domain constant, not configuration (there is no UI to create one). */
public final class Locations {

	public static final Warehouse DC = new Warehouse("dc", "Central DC");
	public static final Store ZURICH = new Store("zurich", "Store Zurich");
	public static final Store BERN = new Store("bern", "Store Bern");
	public static final Store BASEL = new Store("basel", "Store Basel");
	public static final OnlineFc ONLINE = new OnlineFc("online", "Online FC");

	/** The stores, then online - every location but the DC. */
	public static final List<Replenished> REPLENISHED = List.of(ZURICH, BERN, BASEL, ONLINE);

	/** DC first, then the stores, then online - the column order of the admin matrix. */
	public static final List<Location> ALL = List.of(DC, ZURICH, BERN, BASEL, ONLINE);

	private Locations() {}

	public static Optional<Location> byId(String id) {
		return ALL.stream().filter(l -> l.id().equals(id)).findFirst();
	}

	public static Optional<Replenished> replenishedById(String id) {
		return byId(id).filter(Replenished.class::isInstance).map(Replenished.class::cast);
	}

	public static Optional<Store> storeById(String id) {
		return byId(id).filter(Store.class::isInstance).map(Store.class::cast);
	}

	/** Like {@link #byId(String)}, for ids that were stored by this application. */
	public static Location of(String id) {
		return byId(id).orElseThrow(() -> new IllegalArgumentException("unknown location: " + id));
	}

	/** Like {@link #replenishedById(String)}, for ids that were stored by this application. */
	public static Replenished replenishedOf(String id) {
		return replenishedById(id).orElseThrow(() -> new IllegalArgumentException("not a replenished location: " + id));
	}
}
