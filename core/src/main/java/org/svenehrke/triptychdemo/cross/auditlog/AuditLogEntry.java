package org.svenehrke.triptychdemo.cross.auditlog;

import java.time.Instant;

public record AuditLogEntry(String event, String details, Instant timestamp) {}
