package org.svenehrke.triptychdemo.cross.replenishment;

/** {@code Long}, so a message without an id can be told apart from id 0. */
public record ShipmentArrivalMessage(Long shipmentId) {}
