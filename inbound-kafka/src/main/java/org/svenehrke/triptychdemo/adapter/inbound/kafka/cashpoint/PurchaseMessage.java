package org.svenehrke.triptychdemo.adapter.inbound.kafka.cashpoint;

import java.util.List;

public record PurchaseMessage(List<PurchaseMessageItem> items) {}
