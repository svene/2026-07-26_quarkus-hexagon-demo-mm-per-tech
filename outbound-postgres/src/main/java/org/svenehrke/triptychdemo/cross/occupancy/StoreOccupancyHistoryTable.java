package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.jdbc.Db;
import org.svenehrke.triptychdemo.cross.location.Store;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;

import static org.svenehrke.triptychdemo.cross.occupancy.StoreOccupancyTable.COLUMNS;

/**
 * The SQL of the {@code store_occupancy_history} table: every report per store, the same columns as
 * {@code store_occupancy}; only the recent ones are kept.
 */
@ApplicationScoped
public class StoreOccupancyHistoryTable {

    @Inject
    Db db;

    /** A report of the same store and time is there already (redelivered): nothing changes. */
    void insertIfAbsent(StoreOccupancy o) {
        db.update("insert into store_occupancy_history (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " on conflict (storeId, measuredAt) do nothing", StoreOccupancyTable.values(o));
    }

    void deleteBefore(Store store, Instant before) {
        db.update("delete from store_occupancy_history where storeId = ? and measuredAt < ?", store.id(), before);
    }

    List<StoreOccupancy> findSince(Store store, Instant since) {
        return db.query("select " + COLUMNS + " from store_occupancy_history where storeId = ? and measuredAt >= ?"
            + " order by measuredAt", StoreOccupancyTable::map, store.id(), since);
    }

    public int count(Store store) {
        return db.queryInt("select count(*) from store_occupancy_history where storeId = ?", store.id());
    }

    public void deleteAll() {
        db.update("delete from store_occupancy_history");
    }
}
