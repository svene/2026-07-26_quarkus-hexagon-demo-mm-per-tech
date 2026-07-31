package com.example.hexademo.adapter.outbound.mongodb.auditlog;

import com.example.hexademo.core.port.out.AuditLogSPI;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class AuditLogService implements AuditLogSPI {

    @Override
    public void log(String event, String details) {
        var entry = new AuditLogEntry();
        entry.event = event;
        entry.details = details;
        entry.timestamp = Instant.now();
        entry.persist();
    }

    @Override
    public List<com.example.hexademo.core.domain.AuditLogEntry> findRecent(int limit) {
        return AuditLogEntry.<AuditLogEntry>findAll(Sort.descending("timestamp"))
            .page(0, limit)
            .list()
            .stream()
            .map(e -> new com.example.hexademo.core.domain.AuditLogEntry(e.event, e.details, e.timestamp))
            .toList();
    }
}
