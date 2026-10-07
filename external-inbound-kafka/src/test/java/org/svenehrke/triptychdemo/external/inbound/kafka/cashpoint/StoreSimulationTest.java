package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class StoreSimulationTest {

    private static final Instant START = Instant.parse("2026-10-05T00:00:00Z");
    private static final Duration TICK = Duration.ofMillis(100);
    private static final SimulationTiming TIMING = new SimulationTiming(Duration.ofMinutes(1), Duration.ofSeconds(15));

    /** A store too big to fill up: only the tills limit it. */
    private static StoreSimulation store(int tills) {
        return store(1000, tills);
    }

    private static StoreSimulation store(int capacity, int tills) {
        return new StoreSimulation("test", capacity, tills, TIMING, new SplittableRandom(42), START);
    }

    /** Runs the store for {@code duration} in ticks of 100 ms, the clock moved by hand. */
    private static Run run(StoreSimulation store, Duration duration) {
        return run(store, START, duration);
    }

    private static Run run(StoreSimulation store, Instant from, Duration duration) {
        var run = new Run();
        for (var now = from.plus(TICK); !now.isAfter(from.plus(duration)); now = now.plus(TICK)) {
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
        assertThat(store.arrivalsPerSecond(START.plusSeconds(30))).isCloseTo(2.5 * tillThroughput, within(1e-9));
        assertThat(store.arrivalsPerSecond(START.plusSeconds(60))).isCloseTo(0.3 * tillThroughput, within(1e-9));
    }

    @Test
    void opening_a_till_brings_no_more_customers() {
        var store = store(2);
        double before = store.arrivalsPerSecond(START.plusSeconds(30));

        store.setTills(5);

        assertThat(store.arrivalsPerSecond(START.plusSeconds(30))).isEqualTo(before);
        assertThat(store.report(START).tills()).isEqualTo(5);
    }

    @Test
    void opening_tills_shortens_the_queue() {
        var one = store(1);
        var four = store(1);
        four.setTills(4);

        // ends at a rush hour, when a queue builds up at too few tills
        var oneRun = run(one, Duration.ofSeconds(9 * 60 + 40));
        var fourRun = run(four, Duration.ofSeconds(9 * 60 + 40));

        // the same customers (arrivals follow the configured till), they just wait less
        assertThat(fourRun.paid).isGreaterThanOrEqualTo(oneRun.paid);
        assertThat(four.report(START).queuing()).isLessThan(one.report(START).queuing());
    }

    @Test
    void a_closed_busy_till_closes_once_its_customer_has_paid() {
        var store = store(4);
        var peak = START.plusSeconds(30 + 60); // the second rush hour: all tills busy, a queue
        run(store, Duration.between(START, peak));
        var before = store.report(peak);
        assertThat(before.tillsBusy()).isEqualTo(4);
        assertThat(before.queuing()).isPositive();

        store.setTills(1);

        var after = store.report(peak);
        assertThat(after.tills()).isEqualTo(1);
        assertThat(after.tillsBusy()).isEqualTo(1);
        assertThat(after.inside()).isEqualTo(before.inside()); // nobody sent away
        // the 4 customers at the tills still pay (within 18 s, the till time + 20 %), then only 1 till serves
        var finishing = run(store, peak, Duration.ofSeconds(18));
        assertThat(finishing.paid).isGreaterThanOrEqualTo(4);
        var oneTill = run(store, peak.plusSeconds(18), Duration.ofMinutes(2));
        assertThat(oneTill.paid).isLessThanOrEqualTo(2 * 60 / 12 + 1);
    }

    @Test
    void the_report_counts_everybody_inside() {
        var store = store(2);
        run(store, Duration.ofSeconds(45));

        var report = store.report(START);

        assertThat(report.inside()).isEqualTo(store.snapshot().occupancy());
        assertThat(report.inside()).isGreaterThanOrEqualTo(report.queuing() + report.tillsBusy());
        assertThat(report.tills()).isEqualTo(2);
    }

    @Test
    void at_least_one_till_stays_open() {
        assertThatThrownBy(() -> store(2).setTills(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_full_store_turns_new_customers_away() {
        var store = store(6, 1);
        long maxInside = 0;
        for (var now = START.plus(TICK); !now.isAfter(START.plus(Duration.ofMinutes(10))); now = now.plus(TICK)) {
            store.tick(now, TICK);
            maxInside = Math.max(maxInside, store.report(now).inside());
        }

        var snapshot = store.snapshot();
        assertThat(maxInside).isEqualTo(6);
        assertThat(snapshot.turnedAway()).isPositive();
        // who was turned away never entered: nobody counted twice, nobody lost
        assertThat(snapshot.entered()).isEqualTo(snapshot.paid() + snapshot.occupancy());
    }

    @Test
    void the_report_counts_who_was_turned_away_in_the_last_demo_day() {
        var store = store(6, 1);
        var peak = START.plusSeconds(4 * 60 + 40); // after a few demo days, just after a rush hour
        run(store, Duration.between(START, peak));

        var report = store.report(peak);
        assertThat(report.turnedAway()).isPositive();
        assertThat(report.capacity()).isEqualTo(6);
        assertThat(store.report(peak).turnedAway()).isEqualTo(report.turnedAway()); // a report changes nothing
        assertThat(store.report(peak.plus(TIMING.day())).turnedAway()).isZero();
    }

    @Test
    void the_report_counts_who_paid_in_the_last_demo_day() {
        var store = store(1);
        var end = START.plus(TIMING.day().multipliedBy(3));
        run(store, START, TIMING.day().multipliedBy(2)); // the days before: not counted
        var lastDay = run(store, end.minus(TIMING.day()), TIMING.day());

        var report = store.report(end);
        assertThat(report.paid()).isEqualTo(lastDay.paid).isPositive();
        assertThat(store.report(end).paid()).isEqualTo(report.paid()); // a report changes nothing
        assertThat(store.report(end.plus(TIMING.day())).paid()).isZero();
    }

    @Test
    void more_tills_turn_fewer_customers_away_and_sell_more() {
        var configured = store(16, 4);
        var opened = store(16, 4);
        opened.setTills(6);

        var configuredRun = run(configured, Duration.ofMinutes(10));
        var openedRun = run(opened, Duration.ofMinutes(10));

        assertThat(opened.snapshot().turnedAway()).isLessThan(configured.snapshot().turnedAway() / 2);
        assertThat(openedRun.paid).isGreaterThan(configuredRun.paid * 11 / 10);
    }

    @Test
    void capacity_must_be_at_least_one() {
        assertThatThrownBy(() -> store(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
