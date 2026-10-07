package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.location.Store;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class OccupancyService implements OccupancyRepositorySPI {

    @Inject
    StoreOccupancyTable table;
    @Inject
    StoreOccupancyHistoryTable historyTable;

    @Override
    public boolean saveIfNewer(StoreOccupancy occupancy) {
        return table.upsertIfNewer(occupancy);
    }

    @Override
    public List<StoreOccupancy> findAll() {
        return table.findAll();
    }

    @Override
    public void appendToHistory(StoreOccupancy occupancy, Instant keepSince) {
        historyTable.insertIfAbsent(occupancy);
        historyTable.deleteBefore(occupancy.store(), keepSince);
    }

    @Override
    public List<StoreOccupancy> history(Store store, Instant since) {
        return historyTable.findSince(store, since);
    }
}
