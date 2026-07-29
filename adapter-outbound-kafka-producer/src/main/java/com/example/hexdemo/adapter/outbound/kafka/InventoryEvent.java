package com.example.hexdemo.adapter.outbound.kafka;

public record InventoryEvent(String productName, String productType, int availableAmount) {}
