package org.svenehrke.triptychdemo.cross;

import java.util.ArrayList;
import java.util.List;

import static org.svenehrke.triptychdemo.cross.RequestStructureErrorMessages.BODY_REQUIRED;
import static org.svenehrke.triptychdemo.cross.RequestStructureErrorMessages.PRODUCT_NAME_REQUIRED;
import static org.svenehrke.triptychdemo.cross.RequestStructureErrorMessages.QUANTITY_REQUIRED;

/** JSON body of {@code POST /api/products/purchase}. See {@link RequestStructureErrorMessages}. */
public record PurchaseRequest(List<PurchaseRequestItem> items) {

    public static List<String> structureErrors(PurchaseRequest request) {
        if (request == null) return List.of(BODY_REQUIRED);
        if (request.items() == null) return List.of("items is required");
        var errors = new ArrayList<String>();
        for (int i = 0; i < request.items().size(); i++) {
            var item = request.items().get(i);
            if (item == null) {
                errors.add("items[" + i + "]: must not be null");
                continue;
            }
            if (item.productName() == null) errors.add("items[" + i + "]." + PRODUCT_NAME_REQUIRED);
            if (item.quantity() == null) errors.add("items[" + i + "]." + QUANTITY_REQUIRED);
        }
        return errors;
    }
}
