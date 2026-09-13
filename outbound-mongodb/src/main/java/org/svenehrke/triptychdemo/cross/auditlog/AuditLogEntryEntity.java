package org.svenehrke.triptychdemo.cross.auditlog;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;

@MongoEntity(collection = "audit_log")
public class AuditLogEntryEntity extends PanacheMongoEntity {
    public String event;
    public String details;
    public Instant timestamp;
}
