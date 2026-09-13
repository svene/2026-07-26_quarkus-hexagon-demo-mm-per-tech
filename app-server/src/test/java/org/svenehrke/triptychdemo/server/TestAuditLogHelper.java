package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogEntryEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class TestAuditLogHelper {

    public List<String> findEventDetails(String event) {
        return AuditLogEntryEntity.<AuditLogEntryEntity>find("event", event)
            .list()
            .stream()
            .map(e -> e.details)
            .toList();
    }

    public void clearAuditLog() {
        AuditLogEntryEntity.deleteAll();
    }

    public boolean isEmpty() {
        return AuditLogEntryEntity.count() == 0;
    }
}
