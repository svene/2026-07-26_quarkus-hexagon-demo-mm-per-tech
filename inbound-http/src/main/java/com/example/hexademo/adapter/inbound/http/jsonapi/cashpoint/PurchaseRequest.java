package com.example.hexademo.adapter.inbound.http.jsonapi.cashpoint;

import java.util.List;

public record PurchaseRequest(List<PurchaseRequestItem> items) {}
