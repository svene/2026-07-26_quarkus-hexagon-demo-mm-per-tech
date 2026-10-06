package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.random.RandomGenerator;

/**
 * The customers of one physical store. They arrive along a rush-hour curve, shop for 30-60 real minutes, queue for a
 * till and pay there. Each paying customer is one checkout. The tills are the bottleneck: at most
 * {@code tills / tillTime} customers pay per time; at the peak more arrive than that (up to 2.5×), so the till queue
 * grows until the store is full ({@code capacity}): who arrives then turns away, a lost sale. The queue shrinks
 * again in the quiet hours.
 *
 * <p>Tills can be opened and closed ({@link #setTills(int)}); the arrivals stay tied to the configured number of
 * tills (the store's demand), so an extra till lets more of them in - fewer turned away, more sales.
 *
 * <p>Not thread-safe: {@link CashpointStub} advances it from one scheduled method that never runs concurrently.
 */
final class StoreSimulation {

    /** Arrivals relative to what the configured tills can serve: 0.3× in the quiet hours, 2.5× at the peak. */
    private static final double MIN_RUSH = 0.3;
    private static final double MAX_RUSH = 2.5;
    private static final double REAL_MINUTES_PER_DAY = 24 * 60;
    private static final double MIN_STAY_MINUTES = 30;
    private static final double MAX_STAY_MINUTES = 60;
    private static final double TILL_TIME_JITTER = 0.2;

    private final String storeId;
    private final int capacity;
    private final SimulationTiming timing;
    private final RandomGenerator random;
    private final Instant dayStart;

    /** When each shopper is done shopping. */
    private final PriorityQueue<Instant> shoppers = new PriorityQueue<>();
    /** The configured tills: what the arrivals are scaled to. */
    private final int configuredTills;
    /** When the customer at each open till is done paying; {@code null}: the till is free. */
    private final List<Instant> tills = new ArrayList<>();
    /** How many tills should be open; a busy till above it closes once its customer has paid. */
    private int openTills;
    private int queued;
    private long enteredSinceSnapshot;
    private long paidSinceSnapshot;
    private long turnedAwaySinceSnapshot;
    /** When customers were turned away, oldest first; only the last demo day is kept (see {@link #report}). */
    private final ArrayDeque<Instant> turnedAway = new ArrayDeque<>();

    StoreSimulation(String storeId, int capacity, int tills, SimulationTiming timing, RandomGenerator random,
                    Instant dayStart) {
        if (tills < 1) throw new IllegalArgumentException(storeId + ": tills must be at least 1");
        if (capacity < 1) throw new IllegalArgumentException(storeId + ": capacity must be at least 1");
        this.storeId = storeId;
        this.capacity = capacity;
        this.configuredTills = tills;
        setTills(tills);
        this.timing = timing;
        this.random = random;
        this.dayStart = dayStart;
    }

    String storeId() {
        return storeId;
    }

    /** Opens or closes tills; a till closed while a customer pays at it closes once the customer has paid. */
    void setTills(int count) {
        if (count < 1) throw new IllegalArgumentException(storeId + ": tills must be at least 1");
        openTills = count;
        while (tills.size() < openTills) tills.add(null);
        closeFreeTills();
    }

    /**
     * Advances the store to {@code now}, {@code elapsed} after the previous tick.
     *
     * @return the number of customers who paid in this tick, i.e. the checkouts to send
     */
    int tick(Instant now, Duration elapsed) {
        int paid = 0;
        for (int i = 0; i < tills.size(); i++) {
            if (tills.get(i) != null && !tills.get(i).isAfter(now)) {
                tills.set(i, null); // paid and left: frees a place in the store
                paid++;
            }
        }
        closeFreeTills();
        int arrivals = poisson(arrivalsPerSecond(now) * elapsed.toNanos() / 1e9);
        for (int i = 0; i < arrivals; i++) {
            if (occupancy() >= capacity) {
                turnedAway.add(now); // the store is full: a lost sale
                turnedAwaySinceSnapshot++;
            } else {
                shoppers.add(now.plus(stay()));
                enteredSinceSnapshot++;
            }
        }
        while (!shoppers.isEmpty() && !shoppers.peek().isAfter(now)) {
            shoppers.poll();
            queued++;
        }
        for (int i = 0; i < tills.size() && queued > 0; i++) {
            if (tills.get(i) == null) {
                tills.set(i, now.plus(tillTime()));
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

    /** The current state, with the customers who entered / paid / were turned away since the previous snapshot. */
    Snapshot snapshot() {
        var snapshot = new Snapshot(storeId, occupancy(), queued, enteredSinceSnapshot, paidSinceSnapshot,
            turnedAwaySinceSnapshot);
        enteredSinceSnapshot = 0;
        paidSinceSnapshot = 0;
        turnedAwaySinceSnapshot = 0;
        return snapshot;
    }

    record Snapshot(String storeId, int occupancy, int queued, long entered, long paid, long turnedAway) {}

    /**
     * What the store's door counters and tills report: {@code tills} is the number that should be open, so right
     * after closing a busy till {@code tillsBusy} is capped at it (the till closes once its customer has paid).
     * {@code turnedAway}: in the last demo day - a current value like the others, not a count since the last report,
     * so a report can be repeated or lost without harm.
     */
    Occupancy report(Instant now) {
        var dayAgo = now.minus(timing.day());
        while (!turnedAway.isEmpty() && !turnedAway.peek().isAfter(dayAgo)) turnedAway.poll();
        return new Occupancy(storeId, now, occupancy(), capacity, queued, openTills, Math.min(busyTills(), openTills),
            turnedAway.size());
    }

    record Occupancy(String storeId, Instant measuredAt, int inside, int capacity, int queuing, int tills,
                     int tillsBusy, int turnedAway) {}

    /** What the tills can serve, scaled by the rush-hour curve: a cosine over the day, quiet at its start and end. */
    double arrivalsPerSecond(Instant now) {
        double dayNanos = timing.day().toNanos();
        double phase = (Duration.between(dayStart, now).toNanos() % dayNanos) / dayNanos;
        double rush = (MIN_RUSH + MAX_RUSH) / 2 - (MAX_RUSH - MIN_RUSH) / 2 * Math.cos(2 * Math.PI * phase);
        double tillThroughput = configuredTills / (timing.tillTime().toNanos() / 1e9);
        return tillThroughput * rush;
    }

    /** Closes free tills while more are open than should be; free ones first, so no customer is sent away. */
    private void closeFreeTills() {
        for (int i = tills.size() - 1; i >= 0 && tills.size() > openTills; i--) {
            if (tills.get(i) == null) tills.remove(i);
        }
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
