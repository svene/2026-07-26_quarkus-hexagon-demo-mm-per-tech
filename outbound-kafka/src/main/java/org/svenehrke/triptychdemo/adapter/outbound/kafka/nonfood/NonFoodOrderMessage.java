package org.svenehrke.triptychdemo.adapter.outbound.kafka.nonfood;

public record NonFoodOrderMessage(String productName, int quantity) {}
