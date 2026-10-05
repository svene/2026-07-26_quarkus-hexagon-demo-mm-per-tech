package org.svenehrke.triptychdemo.cross;

import java.util.List;

/** {@code limit}: the page shows at most this many entries, newest first. */
public record AuditLogPageVM(List<AuditEntryVM> auditEntries, int limit) {}
