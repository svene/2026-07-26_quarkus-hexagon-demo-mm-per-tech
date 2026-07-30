package com.example.hexademo.external.inbound.kafka.cashpoint;

import java.util.List;

public record PurchaseRequest(List<PurchaseRequestItem> items) {}
