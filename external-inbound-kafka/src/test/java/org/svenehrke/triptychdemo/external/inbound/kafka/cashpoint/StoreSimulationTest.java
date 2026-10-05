package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StoreSimulationTest {

    private static final Instant START = Instant.parse("2026-10-05T00:00:00Z");
    private static final Duration TICK = Duration.ofMillis(100);
    private static final SimulationTiming TIMING = new SimulationTiming(Duration.ofMinutes(1), Duration.ofSeconds(15));

    private static StoreSimulation store(int tills) {
        return new StoreSimulation("test", tills, TIMING, new SplittableRandom(42), START);
    }

    /** Runs the store for {@code duration} in ticks of 100 ms, the clock moved by hand. */
    private static Run run(StoreSimulation store, Duration duration) {
        var run = new Run();
        for (var now = START.plus(TICK); !now.isAfter(START.plus(duration)); now = now.plus(TICK)) {
            int paid = store.tick(now, TICK);
            run.paid += paid;
            if (paid > 0 && run.firstPayment == null) run.firstPayment = now;
        }
        return run;
    }

    private static final class Run {
        long paid;
        Instant firstPayment;
    }

    @Test
    void every_customer_who_entered_pays_exactly_once_or_is_still_inside() {
        var store = store(1);

        var run = run(store, Duration.ofMinutes(10));

        var snapshot = store.snapshot();
        assertThat(snapshot.paid()).isEqualTo(run.paid).isPositive();
        assertThat(snapshot.entered()).isEqualTo(snapshot.paid() + snapshot.occupancy());
    }

    @Test
    void a_customer_pays_after_shopping_and_after_the_till_time() {
        var run = run(store(1), Duration.ofMinutes(10));

        // at least 30 real minutes of shopping (1.25 s at 1 day = 1 min) plus 15 s - 20 % at the till
        assertThat(run.firstPayment).isAfterOrEqualTo(START.plusMillis(1250 + 12_000));
    }

    @Test
    void the_tills_cap_the_purchases() {
        var run = run(store(1), Duration.ofMinutes(10));

        // one till, at least 12 s per customer (15 s - 20 %)
        assertThat(run.paid).isPositive().isLessThanOrEqualTo(10 * 60 / 12);
    }

    @Test
    void more_tills_mean_more_purchases() {
        var one = run(store(1), Duration.ofMinutes(10));
        var four = run(store(4), Duration.ofMinutes(10));

        assertThat(four.paid).isGreaterThan(2 * one.paid);
    }

    @Test
    void arrivals_follow_the_rush_hour_curve_relative_to_what_the_tills_serve() {
        var store = store(3);
        double tillThroughput = 3 / 15.0;

        assertThat(store.arrivalsPerSecond(START)).isCloseTo(0.3 * tillThroughput, within(1e-9));
        assertThat(store.arrivalsPerSecond(START.plusSeconds(30))).isCloseTo(1.5 * tillThroughput, within(1e-9));
        assertThat(store.arrivalsPerSecond(START.plusSeconds(60))).isCloseTo(0.3 * tillThroughput, within(1e-9));
    }
}
