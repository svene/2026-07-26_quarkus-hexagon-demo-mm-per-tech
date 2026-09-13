package org.svenehrke.triptychdemo.core.domain;

import java.time.Instant;

public record AuditLogEntry(String event, String details, Instant timestamp) {}
