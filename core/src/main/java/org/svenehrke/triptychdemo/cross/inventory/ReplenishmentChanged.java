package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Replenished;

/**
 * A request of {@code location} to the DC was created, served (stock moved from the DC) or rejected - also when no
 * stock moved, since the pages showing stock show the requests, too.
 */
public record ReplenishmentChanged(Replenished location) implements InventoryEvent {}
