package org.svenehrke.triptychdemo.cross.reorder;

import org.svenehrke.triptychdemo.cross.location.Locations;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DemandEstimateTest {

	@Test
	void coldStart_assumesTheInitialAvgAsVarianceToo() {
		assertThat(DemandEstimate.initial(ReorderPolicy.STORE)).isEqualTo(new DemandEstimate(10, 10));
		assertThat(DemandEstimate.initial(ReorderPolicy.ONLINE)).isEqualTo(new DemandEstimate(15, 15));
	}

	@Test
	void policy_dependsOnTheKindOfLocation() {
		assertThat(ReorderPolicy.of(Locations.BERN)).isEqualTo(ReorderPolicy.STORE);
		assertThat(ReorderPolicy.of(Locations.ONLINE)).isEqualTo(ReorderPolicy.ONLINE);
	}

	@Test
	void next_movesTheAvgTowardsTheDemand_andGrowsTheVarianceWithTheError() {
		var next = new DemandEstimate(10, 10).next(20, ReorderPolicy.STORE);

		// e = 10: avg + 0.3·10, (1 − 0.3)·(10 + 0.3·10²)
		assertThat(next.avg()).isCloseTo(13, within(1e-9));
		assertThat(next.var()).isCloseTo(28, within(1e-9));
	}

	@Test
	void next_withTheExpectedDemand_keepsTheAvg_andShrinksTheVariance() {
		var next = new DemandEstimate(10, 10).next(10, ReorderPolicy.STORE);

		assertThat(next.avg()).isCloseTo(10, within(1e-9));
		assertThat(next.var()).isCloseTo(7, within(1e-9));
	}

	@Test
	void next_convergesToAConstantDemand() {
		var estimate = DemandEstimate.initial(ReorderPolicy.STORE);
		for (int i = 0; i < 100; i++) estimate = estimate.next(4, ReorderPolicy.STORE);

		assertThat(estimate.avg()).isCloseTo(4, within(1e-6));
		assertThat(estimate.var()).isCloseTo(0, within(1e-6));
	}
}
