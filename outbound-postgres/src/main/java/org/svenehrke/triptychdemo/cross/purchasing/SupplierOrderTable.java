package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.jdbc.Db;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The SQL of the {@code supplier_order} table; its rows map straight to core's {@link SupplierOrder}. */
@ApplicationScoped
public class SupplierOrderTable {

    private static final String COLUMNS = "id, productName, type, quantity, delivered, status, origin, createdAt";

    @Inject
    Db db;

    SupplierOrder create(String productName, ProductType type, int quantity, SupplierOrderOrigin origin) {
        return db.queryOne("insert into supplier_order (productName, type, quantity, delivered, status, origin, createdAt)"
                + " values (?, ?, ?, 0, ?, ?, ?) returning " + COLUMNS,
            SupplierOrderTable::map, productName, type, quantity, SupplierOrderStatus.OPEN, origin, Instant.now())
            .orElseThrow();
    }

    /** What the open orders of {@code productName} will still deliver. */
    int outstanding(String productName) {
        return db.queryInt("select coalesce(sum(quantity - delivered), 0) from supplier_order"
            + " where productName = ? and status = ?", productName, SupplierOrderStatus.OPEN);
    }

    /** Oldest first: the order a delivery closes them in. */
    List<SupplierOrder> findOpenForUpdate(String productName) {
        return db.query("select " + COLUMNS + " from supplier_order where productName = ? and status = ?"
            + " order by id for update", SupplierOrderTable::map, productName, SupplierOrderStatus.OPEN);
    }

    Optional<SupplierOrder> findOpenForUpdate(long id) {
        return db.queryOne("select " + COLUMNS + " from supplier_order where id = ? and status = ? for update",
            SupplierOrderTable::map, id, SupplierOrderStatus.OPEN);
    }

    /** Without a lock: the lock order needs the product before the order is locked. */
    Optional<String> productNameOf(long id) {
        return db.queryOne("select productName from supplier_order where id = ?", rs -> rs.getString(1), id);
    }

    /** {@code quantity} more delivered; delivered once nothing is outstanding. */
    SupplierOrder delivered(SupplierOrder order, int quantity) {
        int delivered = order.delivered() + quantity;
        var status = delivered == order.quantity() ? SupplierOrderStatus.DELIVERED : order.status();
        return setDeliveredAndStatus(order.id(), delivered, status);
    }

    SupplierOrder cancelled(SupplierOrder order) {
        return setDeliveredAndStatus(order.id(), order.delivered(), SupplierOrderStatus.CANCELLED);
    }

    /** Serializes the transactions that call it (released at commit); for checks that have no row to lock yet. */
    void advisoryLock(long key) {
        db.queryOne("select pg_advisory_xact_lock(?)", rs -> true, key);
    }

    List<SupplierOrder> findOpen() {
        return db.query("select " + COLUMNS + " from supplier_order where status = ? order by id",
            SupplierOrderTable::map, SupplierOrderStatus.OPEN);
    }

    public void deleteAll() {
        db.update("delete from supplier_order");
    }

    private SupplierOrder setDeliveredAndStatus(long id, int delivered, SupplierOrderStatus status) {
        return db.queryOne("update supplier_order set delivered = ?, status = ? where id = ? returning " + COLUMNS,
            SupplierOrderTable::map, delivered, status, id)
            .orElseThrow();
    }

    private static SupplierOrder map(ResultSet rs) throws SQLException {
        return new SupplierOrder(rs.getLong("id"), rs.getString("productName"), ProductType.valueOf(rs.getString("type")),
            rs.getInt("quantity"), rs.getInt("delivered"), SupplierOrderStatus.valueOf(rs.getString("status")),
            SupplierOrderOrigin.valueOf(rs.getString("origin")), Db.instant(rs, "createdAt"));
    }
}
