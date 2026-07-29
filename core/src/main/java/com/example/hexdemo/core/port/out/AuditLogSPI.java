package com.example.hexdemo.core.port.out;

public interface AuditLogSPI {
    void log(String event, String details);
}
