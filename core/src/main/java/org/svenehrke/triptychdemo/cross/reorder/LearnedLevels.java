package org.svenehrke.triptychdemo.cross.reorder;

/**
 * Reorder point / order-up-to levels derived from a {@link DemandEstimate}. Rounded up, so a small learned demand
 * never yields {@code min = 0}.
 *
 * @param min reorder point: order when the inventory position falls below it
 * @param max order-up-to level: how far an order fills the inventory position up
 */
public record LearnedLevels(int min, int max) {

	/** Absorbs floating-point noise, so e.g. {@code 10.000000000001} is not rounded up to 11. */
	private static final double EPSILON = 1e-9;

	/** {@code min = ⌈avg·L + z·√var⌉}, {@code max = ⌈min + avg·R⌉}. */
	public static LearnedLevels of(DemandEstimate estimate, ReorderPolicy policy) {
		int min = ceil(estimate.avg() * policy.leadTimePeriods() + policy.safetyFactor() * Math.sqrt(estimate.var()));
		int max = ceil(min + estimate.avg() * policy.coveredPeriods());
		return new LearnedLevels(min, max);
	}

	/**
	 * What to order so the inventory position ({@code available} + {@code outstanding}, i.e. still to be delivered)
	 * reaches {@code max}, if it is below {@code min}; else 0. Counting what is outstanding means repeated sales below
	 * {@code min} do not order again.
	 */
	public int reorderQuantity(int available, int outstanding) {
		int position = available + outstanding;
		return position < min ? max - position : 0;
	}

	private static int ceil(double value) {
		return (int) Math.ceil(value - EPSILON);
	}
}
