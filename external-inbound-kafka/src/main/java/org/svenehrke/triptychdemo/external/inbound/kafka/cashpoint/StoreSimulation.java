package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.PriorityQueue;
import java.util.random.RandomGenerator;

/**
 * The customers of one physical store. They arrive along a rush-hour curve, shop for 30-60 real minutes, queue for a
 * till and pay there. Each paying customer is one checkout. The tills are the bottleneck: at most
 * {@code tills / tillTime} customers pay per time; at the peak more arrive than that, so the till queue grows, and
 * it shrinks again in the quiet hours (on average 0.9× what the tills serve arrive).
 *
 * <p>Not thread-safe: {@link CashpointStub} advances it from one scheduled method that never runs concurrently.
 */
final class StoreSimulation {

    /** Arrivals relative to what the tills can serve: 0.3× in the quiet hours, 1.5× at the peak. */
    private static final double MIN_RUSH = 0.3;
    private static final double MAX_RUSH = 1.5;
    private static final double REAL_MINUTES_PER_DAY = 24 * 60;
    private static final double MIN_STAY_MINUTES = 30;
    private static final double MAX_STAY_MINUTES = 60;
    private static final double TILL_TIME_JITTER = 0.2;

    private final String storeId;
    private final SimulationTiming timing;
    private final RandomGenerator random;
    private final Instant dayStart;

    /** When each shopper is done shopping. */
    private final PriorityQueue<Instant> shoppers = new PriorityQueue<>();
    /** When the customer at each till is done paying; {@code null}: the till is free. */
    private final Instant[] tills;
    private int queued;
    private long enteredSinceSnapshot;
    private long paidSinceSnapshot;

    StoreSimulation(String storeId, int tills, SimulationTiming timing, RandomGenerator random, Instant dayStart) {
        if (tills < 1) throw new IllegalArgumentException(storeId + ": tills must be at least 1");
        this.storeId = storeId;
        this.tills = new Instant[tills];
        this.timing = timing;
        this.random = random;
        this.dayStart = dayStart;
    }

    String storeId() {
        return storeId;
    }

    /**
     * Advances the store to {@code now}, {@code elapsed} after the previous tick.
     *
     * @return the number of customers who paid in this tick, i.e. the checkouts to send
     */
    int tick(Instant now, Duration elapsed) {
        int paid = 0;
        for (int i = 0; i < tills.length; i++) {
            if (tills[i] != null && !tills[i].isAfter(now)) {
                tills[i] = null; // paid and left: frees a place in the store
                paid++;
            }
        }
        int arrivals = poisson(arrivalsPerSecond(now) * elapsed.toNanos() / 1e9);
        for (int i = 0; i < arrivals; i++) {
            shoppers.add(now.plus(stay()));
            enteredSinceSnapshot++;
        }
        while (!shoppers.isEmpty() && !shoppers.peek().isAfter(now)) {
            shoppers.poll();
            queued++;
        }
        for (int i = 0; i < tills.length && queued > 0; i++) {
            if (tills[i] == null) {
                tills[i] = now.plus(tillTime());
                queued--;
            }
        }
        paidSinceSnapshot += paid;
        return paid;
    }

    /** Customers in the store: shopping, queuing for a till or paying. */
    int occupancy() {
        return shoppers.size() + queued + busyTills();
    }

    /** The current state, with the customers who entered / paid since the previous snapshot. */
    Snapshot snapshot() {
        var snapshot = new Snapshot(storeId, occupancy(), queued, enteredSinceSnapshot, paidSinceSnapshot);
        enteredSinceSnapshot = 0;
        paidSinceSnapshot = 0;
        return snapshot;
    }

    record Snapshot(String storeId, int occupancy, int queued, long entered, long paid) {}

    /** What the tills can serve, scaled by the rush-hour curve: a cosine over the day, quiet at its start and end. */
    double arrivalsPerSecond(Instant now) {
        double dayNanos = timing.day().toNanos();
        double phase = (Duration.between(dayStart, now).toNanos() % dayNanos) / dayNanos;
        double rush = (MIN_RUSH + MAX_RUSH) / 2 - (MAX_RUSH - MIN_RUSH) / 2 * Math.cos(2 * Math.PI * phase);
        double tillThroughput = tills.length / (timing.tillTime().toNanos() / 1e9);
        return tillThroughput * rush;
    }

    private int busyTills() {
        int busy = 0;
        for (Instant till : tills) if (till != null) busy++;
        return busy;
    }

    /** 30-60 real minutes, compressed to the demo day. */
    private Duration stay() {
        double minutes = random.nextDouble(MIN_STAY_MINUTES, MAX_STAY_MINUTES);
        return Duration.ofNanos((long) (timing.day().toNanos() * minutes / REAL_MINUTES_PER_DAY));
    }

    /** The till time ± 20%. */
    private Duration tillTime() {
        double factor = random.nextDouble(1 - TILL_TIME_JITTER, 1 + TILL_TIME_JITTER);
        return Duration.ofNanos((long) (timing.tillTime().toNanos() * factor));
    }

    /** Knuth's algorithm; fine for the small means of one tick. */
    private int poisson(double mean) {
        if (mean <= 0) return 0;
        double limit = Math.exp(-mean);
        double product = random.nextDouble();
        int count = 0;
        while (product > limit) {
            count++;
            product *= random.nextDouble();
        }
        return count;
    }
}
