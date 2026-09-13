package org.svenehrke.triptychdemo.external.outbound.kafka.nonfood;

public record DeliveryMessage(String productName, int quantity) {}
