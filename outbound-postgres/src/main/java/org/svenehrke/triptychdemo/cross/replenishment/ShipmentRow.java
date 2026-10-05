package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import java.time.Instant;

/** One row of the {@code shipment} table (see {@link ShipmentTable}); unlike core's {@link Shipment}, with the product's type. */
record ShipmentRow(long id, long requestId, String locationId, String productName, ProductType type, int quantity,
                   ShipmentStatus status, Instant dispatchedAt, Instant arrivedAt) {

    Replenished location() {
        return Locations.replenishedOf(locationId);
    }

    Shipment toDomain() {
        return new Shipment(id, requestId, location(), productName, quantity, status, dispatchedAt, arrivedAt);
    }
}
