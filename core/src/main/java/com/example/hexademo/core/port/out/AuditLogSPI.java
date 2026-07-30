package com.example.hexademo.core.port.out;

public interface AuditLogSPI {
    void log(String event, String details);
}
