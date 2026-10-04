package org.svenehrke.triptychdemo.external.outbound.kafka.carrier;

public record ShipmentMessage(long shipmentId, String locationId, String productName, int quantity) {}
