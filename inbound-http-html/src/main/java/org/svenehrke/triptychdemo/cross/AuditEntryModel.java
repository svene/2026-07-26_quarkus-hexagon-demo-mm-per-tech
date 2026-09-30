package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogEntry;

/** {@code timestamp} is pre-formatted on the server ({@code Instant.toString()}), so the browser shows it as is. */
public record AuditEntryModel(String timestamp, String event, String details) {

    static AuditEntryModel of(AuditLogEntry entry) {
        return new AuditEntryModel(entry.timestamp().toString(), entry.event(), entry.details());
    }
}
