package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.jdbc.Db;
import org.svenehrke.triptychdemo.cross.location.Locations;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** The SQL of the {@code store_occupancy} table; its rows map straight to core's {@link StoreOccupancy}. */
@ApplicationScoped
public class StoreOccupancyTable {

    /** Shared with {@link StoreOccupancyHistoryTable}: both tables have the same columns. */
    static final String COLUMNS = "storeId, measuredAt, inside, capacity, queuing, tills, tillsBusy, paid, turnedAway";

    @Inject
    Db db;

    /**
     * One statement, so concurrent reports of a store need no lock: the row is inserted, or updated only if the
     * report is newer. Returns false if an as new or newer one was stored already.
     */
    boolean upsertIfNewer(StoreOccupancy o) {
        return db.update("insert into store_occupancy (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " on conflict (storeId) do update set measuredAt = excluded.measuredAt, inside = excluded.inside,"
                + " capacity = excluded.capacity, queuing = excluded.queuing, tills = excluded.tills,"
                + " tillsBusy = excluded.tillsBusy, paid = excluded.paid, turnedAway = excluded.turnedAway"
                + " where excluded.measuredAt > store_occupancy.measuredAt",
            values(o)) == 1;
    }

    List<StoreOccupancy> findAll() {
        return db.query("select " + COLUMNS + " from store_occupancy order by storeId", StoreOccupancyTable::map);
    }

    public void deleteAll() {
        db.update("delete from store_occupancy");
    }

    /** The values for {@link #COLUMNS}, in their order. */
    static Object[] values(StoreOccupancy o) {
        return new Object[]{o.store().id(), o.measuredAt(), o.inside(), o.capacity(), o.queuing(), o.tills(),
            o.tillsBusy(), o.paid(), o.turnedAway()};
    }

    static StoreOccupancy map(ResultSet rs) throws SQLException {
        var storeId = rs.getString("storeId");
        var store = Locations.storeById(storeId).orElseThrow(() -> new IllegalStateException("not a store: " + storeId));
        return new StoreOccupancy(store, Db.instant(rs, "measuredAt"), rs.getInt("inside"), rs.getInt("capacity"),
            rs.getInt("queuing"), rs.getInt("tills"), rs.getInt("tillsBusy"), rs.getInt("paid"), rs.getInt("turnedAway"));
    }
}
