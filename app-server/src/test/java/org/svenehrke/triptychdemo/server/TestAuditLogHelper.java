package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.adapter.outbound.mongodb.auditlog.AuditLogEntry;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class TestAuditLogHelper {

    public List<String> findEventDetails(String event) {
        return AuditLogEntry.<AuditLogEntry>find("event", event)
            .list()
            .stream()
            .map(e -> e.details)
            .toList();
    }

    public void clearAuditLog() {
        AuditLogEntry.deleteAll();
    }

    public boolean isEmpty() {
        return AuditLogEntry.count() == 0;
    }
}
