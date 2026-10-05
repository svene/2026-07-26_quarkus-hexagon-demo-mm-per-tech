package org.svenehrke.triptychdemo.cross.inventory;

/** The demo data was reset: every location's stock, request, shipment and supplier order is gone. */
public record InventoryReset() implements InventoryEvent {}
