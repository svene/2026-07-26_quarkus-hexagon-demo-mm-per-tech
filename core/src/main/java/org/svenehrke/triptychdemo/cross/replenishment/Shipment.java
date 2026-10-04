package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Replenished;

import java.time.Instant;

/**
 * Goods on their way from the DC to {@code location}, made by a {@link Transfer} for request {@code requestId}. They
 * have left the DC's stock, and are added to the location's only when they arrive ({@code arrivedAt} is null until
 * then).
 */
public record Shipment(long id, long requestId, Replenished location, String productName, int quantity,
                       ShipmentStatus status, Instant dispatchedAt, Instant arrivedAt) {}
