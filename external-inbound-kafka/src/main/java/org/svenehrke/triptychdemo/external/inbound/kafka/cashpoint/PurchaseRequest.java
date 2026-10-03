package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import java.util.List;

public record PurchaseRequest(String storeId, List<PurchaseRequestItem> items) {}
