package org.svenehrke.triptychdemo.external.outbound.kafka.nonfood;

public record NonFoodOrderMessage(String productName, int quantity) {}
