package org.svenehrke.triptychdemo.cross;

import java.util.List;

public record LocationInventoryVM(String locationId, String locationName, List<LocationProductRowVM> products,
                                  List<PurchaseVM> purchases, List<RequestVM> requests) {}
