package org.svenehrke.triptychdemo.cross.cashpoint;

import java.util.List;

/** {@code storeId}: the id of the store whose cashpoint sold the items, e.g. {@code "bern"}. */
public record PurchaseMessage(String storeId, List<PurchaseMessageItem> items) {}
