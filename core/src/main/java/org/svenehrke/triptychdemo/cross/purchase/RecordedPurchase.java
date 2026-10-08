package org.svenehrke.triptychdemo.cross.purchase;

import org.svenehrke.triptychdemo.cross.location.Replenished;

import java.time.Instant;

/** A completed purchase as stored: how many distinct {@code products} and how many {@code units} in total. */
public record RecordedPurchase(long id, Replenished location, int products, int units, Instant purchasedAt) {}
