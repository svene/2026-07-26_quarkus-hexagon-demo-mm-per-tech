package com.example.hexademo.adapter.outbound.mongodb.auditlog;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;

@MongoEntity(collection = "audit_log")
public class AuditLogEntry extends PanacheMongoEntity {
    public String event;
    public String details;
    public Instant timestamp;
}
