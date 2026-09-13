package org.svenehrke.triptychdemo.cross.auditlog;

import java.util.List;

public interface AuditLogSPI {
	void log(String event, String details);
	List<AuditLogEntry> findRecent(int limit);
}
