package org.svenehrke.triptychdemo.cross.replenishment;

/** Sent to the carrier: {@code shipmentId} identifies the shipment when its arrival is reported back. */
public record ShipmentMessage(long shipmentId, String locationId, String productName, int quantity) {}
