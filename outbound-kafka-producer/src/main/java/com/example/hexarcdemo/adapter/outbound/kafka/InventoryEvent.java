package com.example.hexarcdemo.adapter.outbound.kafka;

public record InventoryEvent(String productName, String productType, int availableAmount) {}
