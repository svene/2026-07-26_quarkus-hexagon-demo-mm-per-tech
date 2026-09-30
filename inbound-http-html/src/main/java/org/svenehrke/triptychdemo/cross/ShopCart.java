package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;

import java.util.ArrayList;
import java.util.List;

/**
 * What the shop form's user put in the basket. Cart semantics (UI concern, not validation): the form
 * submits a row for every product in stock, and a row with a blank or {@code 0} quantity is simply not
 * in the cart.
 */
final class ShopCart {

    record Row(String name, String quantity) {}

    private final List<Row> rows;
    private final List<Row> items;

    private ShopCart(List<Row> rows) {
        this.rows = rows;
        this.items = rows.stream().filter(row -> !isZero(row.quantity())).toList();
    }

    static ShopCart of(List<String> productNames, List<String> quantities) {
        var rows = new ArrayList<Row>();
        if (productNames != null) {
            for (int i = 0; i < productNames.size(); i++) {
                var quantity = quantities != null && i < quantities.size() ? quantities.get(i) : null;
                if (quantity != null && !quantity.isBlank()) rows.add(new Row(productNames.get(i), quantity));
            }
        }
        return new ShopCart(List.copyOf(rows));
    }

    /** Every row with a quantity entered, including {@code 0} - what the user submitted. */
    List<Row> rows() {
        return rows;
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    ParsedPurchase parse() {
        return Purchase.parse(items.stream().map(row -> PurchaseItem.parse(row.name(), row.quantity())).toList());
    }

    /** Prefixed with the product name rather than {@code items[i]}: a shop user can't map an index to a row. */
    List<String> errorsOf(ParsedPurchase.Invalid invalid) {
        return invalid.violationsByItemIndex().entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(v -> items.get(e.getKey()).name() + ": " + v.getMessage()))
            .toList();
    }

    private static boolean isZero(String quantity) {
        return quantity.trim().matches("[+-]?0+");
    }
}
