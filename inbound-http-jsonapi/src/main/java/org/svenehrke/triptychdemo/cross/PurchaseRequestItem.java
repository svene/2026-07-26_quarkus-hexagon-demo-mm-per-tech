package org.svenehrke.triptychdemo.cross;

import java.util.ArrayList;
import java.util.List;

import static org.svenehrke.triptychdemo.cross.RequestStructureErrorMessages.PRODUCT_NAME_REQUIRED;
import static org.svenehrke.triptychdemo.cross.RequestStructureErrorMessages.QUANTITY_REQUIRED;

/** One item of a {@link PurchaseRequest}. See {@link RequestStructureErrorMessages}. */
public record PurchaseRequestItem(String productName, Integer quantity) {

    /** @param path this item's position in the request, e.g. {@code items[0]}; prefixes every message */
    public static List<String> structureErrors(PurchaseRequestItem item, String path) {
        if (item == null) return List.of(path + ": must not be null");
        var errors = new ArrayList<String>();
        if (item.productName() == null) errors.add(path + "." + PRODUCT_NAME_REQUIRED);
        if (item.quantity() == null) errors.add(path + "." + QUANTITY_REQUIRED);
        return errors;
    }
}
