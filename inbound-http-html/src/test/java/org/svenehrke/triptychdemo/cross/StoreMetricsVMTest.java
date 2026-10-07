package org.svenehrke.triptychdemo.cross;

import org.junit.jupiter.api.Test;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.occupancy.StoreOccupancy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreMetricsVMTest {

	private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
	private static final Duration WINDOW = Duration.ofMinutes(10);

	private static StoreOccupancy report(Instant at, int inside, int paid, int turnedAway) {
		return new StoreOccupancy(Locations.BASEL, at, inside, 10, 0, 2, 2, paid, turnedAway);
	}

	@Test
	void t_counts_the_seconds_since_the_windows_start() {
		var metrics = StoreMetricsVM.of(List.of(report(NOW.minus(WINDOW), 1, 0, 0), report(NOW.minusMillis(2500), 1, 0, 0)),
			WINDOW, NOW);

		assertThat(metrics.windowSeconds()).isEqualTo(600);
		assertThat(metrics.points()).extracting(StoreMetricsVM.PointVM::t).containsExactly(0.0, 597.5);
	}

	@Test
	void full_share_and_averages_are_taken_over_the_reports() {
		var metrics = StoreMetricsVM.of(List.of(
			report(NOW.minusSeconds(15), 8, 6, 0),
			report(NOW.minusSeconds(10), 10, 7, 1),
			report(NOW.minusSeconds(5), 10, 8, 2),
			report(NOW, 12, 9, 3)), WINDOW, NOW); // inside above the capacity counts as full, too

		assertThat(metrics.fullPercent()).isEqualTo(75);
		assertThat(metrics.avgPaid()).isEqualTo(7.5);
		assertThat(metrics.avgTurnedAway()).isEqualTo(1.5);
	}

	@Test
	void without_reports_there_are_no_points_and_the_values_are_zero() {
		var metrics = StoreMetricsVM.of(List.of(), WINDOW, NOW);

		assertThat(metrics.points()).isEmpty();
		assertThat(metrics.fullPercent()).isZero();
		assertThat(metrics.avgPaid()).isZero();
		assertThat(metrics.avgTurnedAway()).isZero();
	}
}
