package com.example.hexademo.adapter.outbound.kafka.inventory;

public record InventoryEvent(String productName, String productType, int availableAmount) {}
