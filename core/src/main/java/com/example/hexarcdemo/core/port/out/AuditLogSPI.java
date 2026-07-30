package com.example.hexarcdemo.core.port.out;

public interface AuditLogSPI {
    void log(String event, String details);
}
