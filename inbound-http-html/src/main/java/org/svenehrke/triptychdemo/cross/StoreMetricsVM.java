package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.occupancy.StoreOccupancy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A store's occupancy reports of the last {@code windowSeconds}, for the two charts on {@code /locations}: one point
 * per report, {@code t} in seconds since the window's start (the x axis), to a tenth. {@code paid} and {@code turnedAway} are
 * per demo minute already (each report counts the last demo day), so the points need no aggregation.
 * {@code fullPercent}: the share of the reports that found the store full; {@code avgPaid} / {@code avgTurnedAway}:
 * the mean of the reports' values. Without reports in the window, {@code points} is empty and the averages are 0.
 */
public record StoreMetricsVM(int windowSeconds, List<PointVM> points, int fullPercent, double avgPaid,
                             double avgTurnedAway) {

    public record PointVM(double t, int inside, int capacity, int queuing, int tills, int paid, int turnedAway) {}

    static StoreMetricsVM of(List<StoreOccupancy> history, Duration window, Instant now) {
        var start = now.minus(window);
        var points = history.stream()
            .map(o -> new PointVM(Duration.between(start, o.measuredAt()).toMillis() / 100 / 10.0, o.inside(), o.capacity(),
                o.queuing(), o.tills(), o.paid(), o.turnedAway()))
            .toList();
        long full = points.stream().filter(p -> p.inside() >= p.capacity()).count();
        return new StoreMetricsVM((int) window.toSeconds(), points,
            points.isEmpty() ? 0 : (int) Math.round(100.0 * full / points.size()),
            points.stream().mapToInt(PointVM::paid).average().orElse(0),
            points.stream().mapToInt(PointVM::turnedAway).average().orElse(0));
    }
}
