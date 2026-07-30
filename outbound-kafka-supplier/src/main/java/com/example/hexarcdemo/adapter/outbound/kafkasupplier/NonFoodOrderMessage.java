package com.example.hexarcdemo.adapter.outbound.kafkasupplier;

public record NonFoodOrderMessage(String productName, int quantity) {}
