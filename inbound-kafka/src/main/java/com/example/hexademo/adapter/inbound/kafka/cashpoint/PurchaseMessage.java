package com.example.hexademo.adapter.inbound.kafka.cashpoint;

import java.util.List;

public record PurchaseMessage(List<PurchaseMessageItem> items) {}
