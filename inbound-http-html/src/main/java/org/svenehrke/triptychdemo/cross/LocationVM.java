package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.location.Location;

public record LocationVM(String id, String name) {

    static LocationVM of(Location location) {
        return new LocationVM(location.id(), location.name());
    }
}
