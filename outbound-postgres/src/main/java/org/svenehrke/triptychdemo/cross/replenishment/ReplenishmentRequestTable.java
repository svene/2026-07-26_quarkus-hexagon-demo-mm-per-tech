package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.jdbc.Db;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The SQL of the {@code replenishment_request} table; its rows map straight to core's {@link ReplenishmentRequest}. */
@ApplicationScoped
public class ReplenishmentRequestTable {

    private static final String COLUMNS = "id, locationId, productName, requested, shipped, status, origin, createdAt";

    @Inject
    Db db;

    ReplenishmentRequest create(StockRequest request, RequestOrigin origin) {
        return db.queryOne("insert into replenishment_request (locationId, productName, requested, shipped, status, origin,"
                + " createdAt) values (?, ?, ?, 0, ?, ?, ?) returning " + COLUMNS,
            ReplenishmentRequestTable::map, request.location().id(), request.productName(), request.quantity(),
            RequestStatus.PENDING, origin, Instant.now())
            .orElseThrow();
    }

    /** What is still to be shipped to {@code locationId} by its pending requests of {@code productName}. */
    int outstanding(String locationId, String productName) {
        return db.queryInt("select coalesce(sum(requested - shipped), 0) from replenishment_request"
            + " where locationId = ? and productName = ? and status = ?", locationId, productName, RequestStatus.PENDING);
    }

    /** What is still to be shipped by all pending requests of {@code productName}: the DC's backorders. */
    public int outstanding(String productName) {
        return db.queryInt("select coalesce(sum(requested - shipped), 0) from replenishment_request"
            + " where productName = ? and status = ?", productName, RequestStatus.PENDING);
    }

    /** Oldest first: the order they are locked in, and the tie-break of the fair share. */
    List<ReplenishmentRequest> findPendingForUpdate(String productName) {
        return db.query("select " + COLUMNS + " from replenishment_request where productName = ? and status = ?"
            + " order by id for update", ReplenishmentRequestTable::map, productName, RequestStatus.PENDING);
    }

    Optional<ReplenishmentRequest> findPendingForUpdate(long id) {
        return db.queryOne("select " + COLUMNS + " from replenishment_request where id = ? and status = ? for update",
            ReplenishmentRequestTable::map, id, RequestStatus.PENDING);
    }

    /** Without a lock: the lock order needs the product before the request is locked. */
    Optional<String> productNameOf(long id) {
        return db.queryOne("select productName from replenishment_request where id = ?", rs -> rs.getString(1), id);
    }

    Optional<ReplenishmentRequest> find(long id) {
        return db.queryOne("select " + COLUMNS + " from replenishment_request where id = ?", ReplenishmentRequestTable::map, id);
    }

    /** {@code quantity} more shipped; fulfilled once nothing is outstanding. */
    ReplenishmentRequest shipped(ReplenishmentRequest request, int quantity) {
        int shipped = request.shipped() + quantity;
        var status = shipped == request.requested() ? RequestStatus.FULFILLED : request.status();
        return setShippedAndStatus(request.id(), shipped, status);
    }

    ReplenishmentRequest rejected(ReplenishmentRequest request) {
        return setShippedAndStatus(request.id(), request.shipped(), RequestStatus.REJECTED);
    }

    List<ReplenishmentRequest> findPending() {
        return db.query("select " + COLUMNS + " from replenishment_request where status = ? order by id",
            ReplenishmentRequestTable::map, RequestStatus.PENDING);
    }

    /** Newest first. */
    List<ReplenishmentRequest> findRecent(Replenished location, int limit) {
        return db.query("select " + COLUMNS + " from replenishment_request where locationId = ? order by id desc limit ?",
            ReplenishmentRequestTable::map, location.id(), limit);
    }

    public void deleteAll() {
        db.update("delete from replenishment_request");
    }

    private ReplenishmentRequest setShippedAndStatus(long id, int shipped, RequestStatus status) {
        return db.queryOne("update replenishment_request set shipped = ?, status = ? where id = ? returning " + COLUMNS,
            ReplenishmentRequestTable::map, shipped, status, id)
            .orElseThrow();
    }

    private static ReplenishmentRequest map(ResultSet rs) throws SQLException {
        return new ReplenishmentRequest(rs.getLong("id"), Locations.replenishedOf(rs.getString("locationId")),
            rs.getString("productName"), rs.getInt("requested"), rs.getInt("shipped"),
            RequestStatus.valueOf(rs.getString("status")), RequestOrigin.valueOf(rs.getString("origin")),
            Db.instant(rs, "createdAt"));
    }
}
