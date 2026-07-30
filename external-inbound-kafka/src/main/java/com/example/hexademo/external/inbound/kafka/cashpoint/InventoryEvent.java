package com.example.hexademo.external.inbound.kafka.cashpoint;

public record InventoryEvent(String productName, String productType, int availableAmount) {}
