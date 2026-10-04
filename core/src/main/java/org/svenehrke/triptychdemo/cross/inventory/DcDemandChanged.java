package org.svenehrke.triptychdemo.cross.inventory;

/**
 * A store or the online FC requested {@code productName} from the DC, which lowered the DC's inventory position - it may
 * now be below the DC's reorder point.
 */
public record DcDemandChanged(String productName) implements InventoryEvent {}
