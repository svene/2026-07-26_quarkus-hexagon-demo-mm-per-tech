package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.jdbc.Db;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** The SQL of the {@code shipment} table. */
@ApplicationScoped
public class ShipmentTable {

    private static final String COLUMNS =
        "id, requestId, locationId, productName, type, quantity, status, dispatchedAt, arrivedAt";

    @Inject
    Db db;

    ShipmentRow create(ReplenishmentRequest request, ProductType type, int quantity) {
        return db.queryOne("insert into shipment (requestId, locationId, productName, type, quantity, status, dispatchedAt)"
                + " values (?, ?, ?, ?, ?, ?, ?) returning " + COLUMNS,
            ShipmentTable::map, request.id(), request.location().id(), request.productName(), type, quantity,
            ShipmentStatus.IN_TRANSIT, Instant.now())
            .orElseThrow();
    }

    Optional<ShipmentRow> findInTransitForUpdate(long id) {
        return db.queryOne("select " + COLUMNS + " from shipment where id = ? and status = ? for update",
            ShipmentTable::map, id, ShipmentStatus.IN_TRANSIT);
    }

    ShipmentRow arrived(long id) {
        return db.queryOne("update shipment set status = ?, arrivedAt = ? where id = ? returning " + COLUMNS,
            ShipmentTable::map, ShipmentStatus.ARRIVED, Instant.now(), id)
            .orElseThrow();
    }

    List<ShipmentRow> findInTransit(Instant dispatchedBefore) {
        return db.query("select " + COLUMNS + " from shipment where status = ? and dispatchedAt < ? order by id",
            ShipmentTable::map, ShipmentStatus.IN_TRANSIT, dispatchedBefore);
    }

    /** What is on its way to {@code locationId} of {@code productName}. */
    int inTransit(String locationId, String productName) {
        return db.queryInt("select coalesce(sum(quantity), 0) from shipment"
            + " where locationId = ? and productName = ? and status = ?", locationId, productName, ShipmentStatus.IN_TRANSIT);
    }

    public record Key(String locationId, String productName) {}

    /** What is on its way, per location and product. */
    public Map<Key, Integer> inTransitByLocationAndProduct() {
        return db.query("select locationId, productName, sum(quantity) as quantity from shipment"
                    + " where status = ? group by locationId, productName",
                rs -> Map.entry(new Key(rs.getString("locationId"), rs.getString("productName")), rs.getInt("quantity")),
                ShipmentStatus.IN_TRANSIT)
            .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void deleteAll() {
        db.update("delete from shipment");
    }

    private static ShipmentRow map(ResultSet rs) throws SQLException {
        return new ShipmentRow(rs.getLong("id"), rs.getLong("requestId"), rs.getString("locationId"),
            rs.getString("productName"), ProductType.valueOf(rs.getString("type")), rs.getInt("quantity"),
            ShipmentStatus.valueOf(rs.getString("status")), Db.instant(rs, "dispatchedAt"), Db.instant(rs, "arrivedAt"));
    }
}
