package com.example.hexademo.adapter.outbound.kafkasupplier;

public record NonFoodOrderMessage(String productName, int quantity) {}
