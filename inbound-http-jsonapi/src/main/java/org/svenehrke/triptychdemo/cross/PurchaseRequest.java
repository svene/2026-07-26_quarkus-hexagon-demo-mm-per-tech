package org.svenehrke.triptychdemo.cross;

import java.util.List;
import java.util.stream.IntStream;

import static org.svenehrke.triptychdemo.cross.RequestStructureErrorMessages.BODY_REQUIRED;

/** JSON body of {@code POST /api/products/purchase}. See {@link RequestStructureErrorMessages}. */
public record PurchaseRequest(List<PurchaseRequestItem> items) {

    public static List<String> structureErrors(PurchaseRequest request) {
        if (request == null) return List.of(BODY_REQUIRED);
        if (request.items() == null) return List.of("items is required");
        return IntStream.range(0, request.items().size())
            .mapToObj(i -> PurchaseRequestItem.structureErrors(request.items().get(i), "items[" + i + "]"))
            .flatMap(List::stream)
            .toList();
    }
}
