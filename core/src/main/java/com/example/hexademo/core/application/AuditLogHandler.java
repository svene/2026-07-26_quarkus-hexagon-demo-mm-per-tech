package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.AuditLogEntry;
import com.example.hexademo.core.api.AuditLogAPI;
import com.example.hexademo.core.spi.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class AuditLogHandler implements AuditLogAPI {

    @Inject AuditLogSPI auditLog;

    @Override
    public List<AuditLogEntry> recent(int limit) {
        return auditLog.findRecent(limit);
    }
}
