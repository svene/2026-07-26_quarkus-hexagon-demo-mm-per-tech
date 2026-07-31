package com.example.hexademo.core.domain;

import java.time.Instant;

public record AuditLogEntry(String event, String details, Instant timestamp) {}
