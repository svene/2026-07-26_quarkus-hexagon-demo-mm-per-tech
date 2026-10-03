package org.svenehrke.triptychdemo.cross.replenishment;

/** {@code quantity} moved from the DC to the request's location; {@code request} is its state afterwards. */
public record Transfer(ReplenishmentRequest request, int quantity) {}
