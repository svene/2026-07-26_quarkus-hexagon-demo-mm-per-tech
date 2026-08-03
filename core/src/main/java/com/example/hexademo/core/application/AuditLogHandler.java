package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.AuditLogEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class AuditLogHandler implements APIs.AuditLogAPI {

    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void log(String event, String details) {
        auditLog.log(event, details);
    }

    @Override
    public List<AuditLogEntry> recent(int limit) {
        return auditLog.findRecent(limit);
    }
}
