package com.example.hexademo.adapter.outbound.kafka;

public record InventoryEvent(String productName, String productType, int availableAmount) {}
