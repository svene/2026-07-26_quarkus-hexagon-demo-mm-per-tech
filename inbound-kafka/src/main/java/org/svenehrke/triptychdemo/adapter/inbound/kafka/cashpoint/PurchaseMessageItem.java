package org.svenehrke.triptychdemo.adapter.inbound.kafka.cashpoint;

public record PurchaseMessageItem(String productName, int quantity) {}
