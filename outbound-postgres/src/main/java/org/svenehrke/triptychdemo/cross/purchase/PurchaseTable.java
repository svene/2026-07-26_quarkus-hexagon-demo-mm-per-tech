package org.svenehrke.triptychdemo.cross.purchase;

import org.svenehrke.triptychdemo.cross.jdbc.Db;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/** The SQL of the {@code purchase} table: the completed purchases per location; only the recent ones are kept. */
@ApplicationScoped
public class PurchaseTable {

    private static final String COLUMNS = "id, locationId, products, units, purchasedAt";

    @Inject
    Db db;

    void insert(Replenished location, int products, int units, Instant purchasedAt) {
        db.update("insert into purchase (locationId, products, units, purchasedAt) values (?, ?, ?, ?)",
            location.id(), products, units, purchasedAt);
    }

    void deleteBefore(Replenished location, Instant before) {
        db.update("delete from purchase where locationId = ? and purchasedAt < ?", location.id(), before);
    }

    /** Newest first. */
    List<RecordedPurchase> findRecent(Replenished location, int limit) {
        return db.query("select " + COLUMNS + " from purchase where locationId = ? order by id desc limit ?",
            PurchaseTable::map, location.id(), limit);
    }

    public int count(Replenished location) {
        return db.queryInt("select count(*) from purchase where locationId = ?", location.id());
    }

    public void deleteAll() {
        db.update("delete from purchase");
    }

    private static RecordedPurchase map(ResultSet rs) throws SQLException {
        return new RecordedPurchase(rs.getLong("id"), Locations.replenishedOf(rs.getString("locationId")),
            rs.getInt("products"), rs.getInt("units"), Db.instant(rs, "purchasedAt"));
    }
}
