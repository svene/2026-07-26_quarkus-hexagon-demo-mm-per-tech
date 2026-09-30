package org.svenehrke.triptychdemo.cross;

import java.util.ArrayList;
import java.util.List;

import static org.svenehrke.triptychdemo.cross.RequestStructureErrorMessages.BODY_REQUIRED;
import static org.svenehrke.triptychdemo.cross.RequestStructureErrorMessages.PRODUCT_NAME_REQUIRED;
import static org.svenehrke.triptychdemo.cross.RequestStructureErrorMessages.QUANTITY_REQUIRED;

/** JSON body of {@code POST /api/products/order-...}. See {@link RequestStructureErrorMessages}. */
public record VegetableOrderRequest(String productName, Integer quantity) implements OrderRequest {

    public static List<String> structureErrors(VegetableOrderRequest request) {
        if (request == null) return List.of(BODY_REQUIRED);
        var errors = new ArrayList<String>();
        if (request.productName() == null) errors.add(PRODUCT_NAME_REQUIRED);
        if (request.quantity() == null) errors.add(QUANTITY_REQUIRED);
        return errors;
    }
}
