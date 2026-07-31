package com.example.hexademo.core.port.in;

import com.example.hexademo.core.domain.AuditLogEntry;
import java.util.List;

public interface AuditLogAPI {
    List<AuditLogEntry> recent(int limit);
}
