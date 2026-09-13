package org.svenehrke.triptychdemo.cross.auditlog;

import java.util.List;

public interface AuditLogAPI {
	void log(String event, String details);
	List<AuditLogEntry> recent(int limit);
}
