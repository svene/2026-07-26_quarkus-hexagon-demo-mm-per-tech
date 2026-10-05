package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import java.time.Duration;

/**
 * The time scale of the store simulation. {@code day}: one real day in the demo (1 min, like
 * {@code inventory.demand-period}), which compresses the customers' shopping time. {@code tillTime}: how long one
 * customer takes at a till - set in demo time, not compressed, since the tills throttle the number of purchases.
 */
public record SimulationTiming(Duration day, Duration tillTime) {}
