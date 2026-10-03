package org.svenehrke.triptychdemo.cross.reorder;

import org.svenehrke.triptychdemo.cross.location.OnlineFc;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.location.Store;

/**
 * How demand is learned and turned into reorder levels at one kind of location - see {@link DemandEstimate} and
 * {@link LearnedLevels}. Domain constants like {@code Locations}, tuned in code. The DC has no policy yet: it only
 * serves requests, it neither learns levels nor orders from suppliers.
 *
 * @param alpha             smoothing factor: the weight of the latest period against the history
 * @param safetyFactor      z: safety stock in standard deviations of the demand (2 ≈ 97.7 % of periods without a
 *                          stock-out)
 * @param leadTimePeriods   L: periods until a replenishment arrives
 * @param coveredPeriods    R: periods one replenishment should cover
 * @param initialAvgDemand  the demand assumed before anything was learned (cold start)
 */
public record ReorderPolicy(double alpha, double safetyFactor, double leadTimePeriods, double coveredPeriods,
                            double initialAvgDemand) {

	public static final ReorderPolicy STORE = new ReorderPolicy(0.3, 2, 1, 3, 10);
	public static final ReorderPolicy ONLINE = new ReorderPolicy(0.3, 2, 1, 3, 15);

	public static ReorderPolicy of(Replenished location) {
		return switch (location) {
			case Store store -> STORE;
			case OnlineFc onlineFc -> ONLINE;
		};
	}
}
