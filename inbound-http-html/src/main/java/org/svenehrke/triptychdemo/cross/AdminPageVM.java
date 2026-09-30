package org.svenehrke.triptychdemo.cross;

import java.util.List;

public record AdminPageVM(List<ProductRowVM> products, List<AuditEntryVM> auditEntries) {}
