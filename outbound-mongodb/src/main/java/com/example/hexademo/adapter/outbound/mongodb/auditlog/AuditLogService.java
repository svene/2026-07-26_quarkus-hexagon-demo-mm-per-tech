package com.example.hexademo.adapter.outbound.mongodb.auditlog;

import com.example.hexademo.core.port.out.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

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
}
