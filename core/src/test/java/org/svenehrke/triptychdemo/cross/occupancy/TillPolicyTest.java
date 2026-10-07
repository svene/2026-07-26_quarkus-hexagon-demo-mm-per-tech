package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.location.Locations;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TillPolicyTest {

	/** Zurich: capacity 16. */
	private static Optional<Integer> decide(int inside, int queuing, int tills, int tillsBusy) {
		return TillPolicy.decide(new StoreOccupancy(Locations.ZURICH, Instant.now(), inside, 16, queuing, tills, tillsBusy, 0, 0))
			.map(TillCount::tills);
	}

	@Test
	void moreThanOneQueuingPerTill_opensATill() {
		assertThat(decide(12, 5, 4, 4)).contains(5);
		assertThat(decide(12, 4, 4, 4)).isEmpty();
	}

	@Test
	void aFullStoreWithEveryTillBusy_opensATill() {
		assertThat(decide(16, 0, 4, 4)).contains(5);
	}

	@Test
	void aFullStoreWithAFreeTill_keepsItsTills() {
		assertThat(decide(16, 0, 4, 3)).isEmpty();
	}

	@Test
	void noQueueAndTwoFreeTills_closesATill() {
		assertThat(decide(5, 0, 4, 2)).contains(3);
		assertThat(decide(5, 0, 4, 0)).contains(3);
	}

	@Test
	void inBetween_keepsTheTills() {
		assertThat(decide(8, 0, 4, 3)).isEmpty();
		assertThat(decide(8, 1, 4, 2)).isEmpty();
		assertThat(decide(8, 3, 4, 4)).isEmpty();
	}

	@Test
	void staysWithin1And8Tills() {
		assertThat(decide(16, 12, 8, 8)).isEmpty();
		assertThat(decide(0, 0, 1, 0)).isEmpty();
		assertThat(decide(16, 12, 7, 7)).contains(8);
		assertThat(decide(0, 0, 2, 0)).contains(1);
	}
}
