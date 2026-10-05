package org.svenehrke.triptychdemo.cross;

import java.util.List;

/** {@code catalog}: the products the order forms offer. */
public record AdminPageVM(List<CatalogProductVM> catalog, List<LocationVM> locations, List<StockRowVM> products,
                          List<RequestVM> pendingRequests, List<SupplierOrderVM> supplierOrders) {}
