package org.svenehrke.triptychdemo.cross.occupancy;

import java.util.Optional;

/**
 * When a store should open or close a till, judged from one occupancy report - one till at a time. A till costs staff,
 * so idle tills close again; otherwise "all {@value TillCount#MAX_TILLS} open" would always win. The two rules never
 * both apply (open needs a queue or every till busy, close needs no queue and two free tills), and a till that has just
 * opened is free, so the next report doesn't close it right away.
 */
public final class TillPolicy {

	private TillPolicy() {}

	/** The new number of tills, or empty to keep them. */
	public static Optional<TillCount> decide(StoreOccupancy o) {
		if (shouldOpen(o) && o.tills() < TillCount.MAX_TILLS) return Optional.of(new TillCount(o.store(), o.tills() + 1));
		if (shouldClose(o) && o.tills() > 1) return Optional.of(new TillCount(o.store(), o.tills() - 1));
		return Optional.empty();
	}

	/**
	 * More than one customer per open till is queuing, or the store is full (turning customers away) and every till
	 * is busy. A full store with a free till needs no new one: its customers are still shopping.
	 */
	static boolean shouldOpen(StoreOccupancy o) {
		return o.queuing() > o.tills() || (o.inside() >= o.capacity() && o.tillsBusy() >= o.tills());
	}

	/** Nobody is queuing and at least two tills are free: one of them can close and the store still has a spare one. */
	static boolean shouldClose(StoreOccupancy o) {
		return o.queuing() == 0 && o.tillsBusy() <= o.tills() - 2;
	}
}
