package org.svenehrke.triptychdemo.cross.auditlog;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class AuditLogService implements AuditLogSPI {

    @Override
    public void log(String event, String details) {
        var entry = new AuditLogEntryEntity();
        entry.event = event;
        entry.details = details;
        entry.timestamp = Instant.now();
        entry.persist();
    }

    @Override
    public List<AuditLogEntry> findRecent(int limit) {
        return AuditLogEntryEntity.<AuditLogEntryEntity>findAll(Sort.descending("timestamp"))
            .page(0, limit)
            .list()
            .stream()
            .map(e -> new AuditLogEntry(e.event, e.details, e.timestamp))
            .toList();
    }
}
