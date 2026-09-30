package org.svenehrke.triptychdemo.cross;

import java.util.List;

public record AdminPageModel(List<ProductRowModel> products, List<AuditEntryModel> auditEntries) {}
