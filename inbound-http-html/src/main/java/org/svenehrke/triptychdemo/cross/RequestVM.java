package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentRequest;

/** {@code createdAt} is pre-formatted on the server ({@code Instant.toString()}), like the audit timestamps. */
public record RequestVM(long id, String locationName, String productName, int requested, int delivered,
                        String status, String origin, String createdAt) {

    static RequestVM of(ReplenishmentRequest request) {
        return new RequestVM(request.id(), request.location().name(), request.productName(), request.requested(),
            request.delivered(), request.status().name(), request.origin().name(), request.createdAt().toString());
    }
}
