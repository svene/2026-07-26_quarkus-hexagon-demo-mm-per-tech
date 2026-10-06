package org.svenehrke.triptychdemo.cross.occupancy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class OccupancyService implements OccupancyRepositorySPI {

    @Inject
    StoreOccupancyTable table;

    @Override
    public boolean saveIfNewer(StoreOccupancy occupancy) {
        return table.upsertIfNewer(occupancy);
    }

    @Override
    public List<StoreOccupancy> findAll() {
        return table.findAll();
    }
}
