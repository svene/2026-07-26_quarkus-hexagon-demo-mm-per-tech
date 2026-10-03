package org.svenehrke.triptychdemo.cross.inventory;

/**
 * A supplier delivery of {@code productName} has been added to the DC stock. Whatever reacts to it (serving the
 * pending requests) runs decoupled from the delivery - a failing reaction must not make the Kafka receiver retry,
 * and so count, the delivery twice.
 */
public record DeliveredToDc(String productName) implements InventoryEvent {}
