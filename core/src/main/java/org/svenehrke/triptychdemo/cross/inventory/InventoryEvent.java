package org.svenehrke.triptychdemo.cross.inventory;

/**
 * A committed change of stock (or of the requests for it), fired by core with {@code Event.fireAsync} - one event
 * per change, after the commit, so an observer that re-reads sees it. Observers are inbound adapters: one can react
 * to a specific event (e.g. {@link DeliveredToDc}), or to all of them by observing this type (the live updates of
 * the pages). Async, so no observer can slow down or fail the change.
 * <p>
 * In-process only: with several app instances, an observer on one would miss changes made on another.
 * <p>
 * The permitted events live in this package because core is not a named module: a sealed type's permitted
 * subclasses then have to share its package.
 */
public sealed interface InventoryEvent permits DeliveredToDc, StockDeducted, ReplenishmentChanged, LevelsRecalculated {}
