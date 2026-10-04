package org.svenehrke.triptychdemo.cross.inventory;

/** A supplier order was placed, or cancelled because sending it failed. (A delivery fires {@link DeliveredToDc}.) */
public record SupplierOrdersChanged() implements InventoryEvent {}
