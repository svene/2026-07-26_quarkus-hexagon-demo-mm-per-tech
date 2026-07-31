package com.example.hexademo.core.spi;

import com.example.hexademo.core.domain.AuditLogEntry;
import java.util.List;

public interface AuditLogSPI {
    void log(String event, String details);
    List<AuditLogEntry> findRecent(int limit);
}
