package org.svenehrke.triptychdemo.cross.auditlog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class AuditLogHandler {

    @Inject
    AuditLogSPI auditLog;

    public void log(String event, String details) {
        auditLog.log(event, details);
    }

    public List<AuditLogEntry> recent(int limit) {
        return auditLog.findRecent(limit);
    }
}
