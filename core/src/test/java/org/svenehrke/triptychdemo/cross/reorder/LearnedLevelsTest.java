package org.svenehrke.triptychdemo.cross.reorder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearnedLevelsTest {

	@Test
	void coldStart_alreadyHasASafetyStock() {
		// min = ⌈10·1 + 2·√10⌉ = ⌈16.32⌉, max = ⌈17 + 10·3⌉
		assertThat(LearnedLevels.of(DemandEstimate.initial(ReorderPolicy.STORE), ReorderPolicy.STORE))
			.isEqualTo(new LearnedLevels(17, 47));
		// min = ⌈15·1 + 2·√15⌉ = ⌈22.75⌉, max = ⌈23 + 15·3⌉
		assertThat(LearnedLevels.of(DemandEstimate.initial(ReorderPolicy.ONLINE), ReorderPolicy.ONLINE))
			.isEqualTo(new LearnedLevels(23, 68));
	}

	@Test
	void levels_areRoundedUp_soASmallDemandNeverYieldsZero() {
		assertThat(LearnedLevels.of(new DemandEstimate(0.1, 0.0001), ReorderPolicy.STORE))
			.isEqualTo(new LearnedLevels(1, 2));
	}

	@Test
	void levels_thatComeOutWhole_areNotRoundedUpByFloatingPointNoise() {
		// min = 4·1 + 2·√4 = 8, max = 8 + 4·3 = 20; 0.1 + 0.2 style noise must not make it 9 / 21
		var estimate = new DemandEstimate(4 + 1e-12, 4 + 1e-12);

		assertThat(LearnedLevels.of(estimate, ReorderPolicy.STORE)).isEqualTo(new LearnedLevels(8, 20));
	}

	@Test
	void reorderQuantity_fillsTheInventoryPositionUpToMax_whenBelowMin() {
		assertThat(new LearnedLevels(8, 20).reorderQuantity(7, 0)).isEqualTo(13);
		assertThat(new LearnedLevels(8, 20).reorderQuantity(0, 0)).isEqualTo(20);
	}

	@Test
	void reorderQuantity_isZero_exactlyAtMin() {
		assertThat(new LearnedLevels(8, 20).reorderQuantity(8, 0)).isZero();
	}

	@Test
	void reorderQuantity_countsWhatIsOutstanding() {
		// position 3 + 5 = 8: the outstanding request covers the gap, so nothing is ordered again
		assertThat(new LearnedLevels(8, 20).reorderQuantity(3, 5)).isZero();
		// position 3 + 2 = 5: orders the rest up to max
		assertThat(new LearnedLevels(8, 20).reorderQuantity(3, 2)).isEqualTo(15);
	}

	@Test
	void reorderQuantity_isZero_aboveMax() {
		assertThat(new LearnedLevels(8, 20).reorderQuantity(30, 0)).isZero();
	}

	@Test
	void reorderQuantity_alsoCoversBackorders_whenOutstandingIsNegative() {
		// the DC: 0 available, 30 open in supplier orders, 50 requested by the locations → position −20
		assertThat(new LearnedLevels(8, 20).reorderQuantity(0, 30 - 50)).isEqualTo(40);
	}

	@Test
	void of_dcColdStart() {
		// avg 60, var 60, L 2, R 3: min ⌈120 + 2·√60⌉ = 136, max ⌈136 + 180⌉ = 316
		assertThat(LearnedLevels.of(DemandEstimate.initial(ReorderPolicy.DC), ReorderPolicy.DC))
			.isEqualTo(new LearnedLevels(136, 316));
	}
}
