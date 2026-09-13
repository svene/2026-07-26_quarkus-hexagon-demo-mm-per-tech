package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

public record PurchaseRequestItem(String productName, int quantity) {}
