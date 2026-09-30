package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogEntry;

/** {@code timestamp} is pre-formatted on the server ({@code Instant.toString()}), so the browser shows it as is. */
public record AuditEntryVM(String timestamp, String event, String details) {

    static AuditEntryVM of(AuditLogEntry entry) {
        return new AuditEntryVM(entry.timestamp().toString(), entry.event(), entry.details());
    }
}
