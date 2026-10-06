package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/** The latest audit log entries; no live updates - the page reloads them on its Refresh button. */
@RunOnVirtualThread
@Path("/audit-log")
public class AuditLogReceiver {

    static final int LIMIT = 300;

    @Inject
    AuditLogHandler auditLogHandler;

    /** The static page shell; its {@code #app} element loads {@link #page()} and renders it in the browser. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String shell() {
        return PageShell.render("/shells/audit-log.html", Map.of());
    }

    @GET
    @Path("/page")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse page() {
        var entries = auditLogHandler.recent(LIMIT).stream().map(AuditEntryVM::of).toList();
        return UiResponse.of(UiRoute.AuditLogPage, new AuditLogPageVM(entries, LIMIT));
    }
}
