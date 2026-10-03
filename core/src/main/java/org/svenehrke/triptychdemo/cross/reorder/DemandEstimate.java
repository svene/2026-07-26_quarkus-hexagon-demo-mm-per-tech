package org.svenehrke.triptychdemo.cross.reorder;

/**
 * The learned demand per period of one product at one location: exponentially smoothed mean and variance.
 *
 * @param avg mean demand per period
 * @param var variance of the demand per period
 */
public record DemandEstimate(double avg, double var) {

	/** Cold start: {@code var = avg} (Poisson-like), so there is a safety stock from the start instead of {@code √0}. */
	public static DemandEstimate initial(ReorderPolicy policy) {
		return new DemandEstimate(policy.initialAvgDemand(), policy.initialAvgDemand());
	}

	/** Folds one closed period's demand into the estimate (exponentially weighted mean and variance). */
	public DemandEstimate next(int periodDemand, ReorderPolicy policy) {
		double alpha = policy.alpha();
		double error = periodDemand - avg;
		return new DemandEstimate(avg + alpha * error, (1 - alpha) * (var + alpha * error * error));
	}
}
