package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentRequest;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** {@code createdAt} is pre-formatted on the server as local date and time with seconds precision ({@code yyyy-MM-dd HH:mm:ss}). */
public record RequestVM(long id, String locationName, String productName, int requested, int shipped,
                        String status, String origin, String createdAt) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    static RequestVM of(ReplenishmentRequest request) {
        return new RequestVM(request.id(), request.location().name(), request.productName(), request.requested(),
            request.shipped(), request.status().name(), request.origin().name(), TIME.format(request.createdAt()));
    }
}
