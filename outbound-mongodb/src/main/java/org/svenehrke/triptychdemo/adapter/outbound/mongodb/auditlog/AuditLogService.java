package org.svenehrke.triptychdemo.adapter.outbound.mongodb.auditlog;

import org.svenehrke.triptychdemo.core.application.SPIs;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class AuditLogService implements SPIs.AuditLogSPI {

    @Override
    public void log(String event, String details) {
        var entry = new AuditLogEntry();
        entry.event = event;
        entry.details = details;
        entry.timestamp = Instant.now();
        entry.persist();
    }

    @Override
    public List<org.svenehrke.triptychdemo.core.domain.AuditLogEntry> findRecent(int limit) {
        return AuditLogEntry.<AuditLogEntry>findAll(Sort.descending("timestamp"))
            .page(0, limit)
            .list()
            .stream()
            .map(e -> new org.svenehrke.triptychdemo.core.domain.AuditLogEntry(e.event, e.details, e.timestamp))
            .toList();
    }
}
