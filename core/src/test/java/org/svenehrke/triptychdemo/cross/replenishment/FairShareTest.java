package org.svenehrke.triptychdemo.cross.replenishment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FairShareTest {

	@Test
	void enoughStock_servesEveryoneInFull() {
		assertThat(FairShare.allocate(100, List.of(39, 39))).containsExactly(39, 39);
		assertThat(FairShare.allocate(78, List.of(39, 39))).containsExactly(39, 39);
	}

	@Test
	void shortfall_isSharedInProportionToWhatEachNeeds() {
		// the dev-demo case: 50 Bananas for 3 stores (39 each) and the online FC (58), Σ 175
		assertThat(FairShare.allocate(50, List.of(39, 39, 39, 58))).containsExactly(11, 11, 11, 17);
	}

	@Test
	void leftoverUnits_goToTheLargestRemainders() {
		// 5·1/6 = 0.83, 5·2/6 = 1.67, 5·3/6 = 2.5 → 0, 1, 2 and two units left for the 0.83 and the 0.67
		assertThat(FairShare.allocate(5, List.of(1, 2, 3))).containsExactly(1, 2, 2);
	}

	@Test
	void equalRemainders_favourTheOlderRequest() {
		assertThat(FairShare.allocate(7, List.of(5, 5))).containsExactly(4, 3);
		assertThat(FairShare.allocate(3, List.of(2, 2, 2, 2))).containsExactly(1, 1, 1, 0);
	}

	@Test
	void handsOutExactlyTheStock_neverMoreThanARequestNeeds() {
		var shares = FairShare.allocate(97, List.of(1, 50, 13, 200, 7));

		assertThat(shares.stream().mapToInt(Integer::intValue).sum()).isEqualTo(97);
		var outstanding = List.of(1, 50, 13, 200, 7);
		for (int i = 0; i < shares.size(); i++) assertThat(shares.get(i)).isBetween(0, outstanding.get(i));
	}

	@Test
	void noStock_handsOutNothing() {
		assertThat(FairShare.allocate(0, List.of(5, 3))).containsExactly(0, 0);
	}

	@Test
	void aSingleRequest_getsWhatThereIs() {
		assertThat(FairShare.allocate(3, List.of(5))).containsExactly(3);
		assertThat(FairShare.allocate(0, List.of())).isEmpty();
	}
}
