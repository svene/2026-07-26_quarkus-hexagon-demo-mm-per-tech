package com.example.hexademo.core.api;

import com.example.hexademo.core.domain.AuditLogEntry;
import java.util.List;

public interface AuditLogAPI {
    List<AuditLogEntry> recent(int limit);
}
