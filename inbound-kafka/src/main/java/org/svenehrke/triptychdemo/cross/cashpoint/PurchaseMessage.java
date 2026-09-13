package org.svenehrke.triptychdemo.cross.cashpoint;

import java.util.List;

public record PurchaseMessage(List<PurchaseMessageItem> items) {}
