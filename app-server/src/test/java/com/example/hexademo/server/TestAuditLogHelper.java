package com.example.hexademo.server;

import com.example.hexademo.adapter.outbound.mongodb.AuditLogEntry;
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
