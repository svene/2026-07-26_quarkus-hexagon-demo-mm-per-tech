package org.svenehrke.triptychdemo.cross;

import java.util.List;

public record AdminPageVM(List<LocationVM> locations, List<StockRowVM> products, List<RequestVM> pendingRequests,
                          List<SupplierOrderVM> supplierOrders, List<AuditEntryVM> auditEntries) {}
