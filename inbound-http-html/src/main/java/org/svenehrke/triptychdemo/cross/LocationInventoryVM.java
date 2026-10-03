package org.svenehrke.triptychdemo.cross;

import java.util.List;

public record LocationInventoryVM(String locationId, List<LocationProductRowVM> products, List<RequestVM> requests) {}
