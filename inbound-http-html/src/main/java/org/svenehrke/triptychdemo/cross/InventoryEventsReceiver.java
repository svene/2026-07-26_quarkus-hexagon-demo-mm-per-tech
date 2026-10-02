package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.inventory.InventoryChangesHandler;

import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.time.Duration;

/** Inventory change notifications for every page that shows the inventory ({@code /shop}, {@code /admin}). */
@Path("/inventory")
public class InventoryEventsReceiver {

    /** Lets the server notice closed connections (and keeps proxies from dropping an idle one). */
    static final Duration HEARTBEAT = Duration.ofSeconds(15);

    @Inject
    InventoryChangesHandler inventoryChanges;

    /**
     * SSE stream for the page shells: an {@code inventoryChanged} event per inventory change, on which a page
     * re-fetches its inventory fragment. One is also sent on (re)connect, so changes made while the
     * browser was disconnected aren't missed.
     */
    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> events(@Context Sse sse) {
        var changed = sse.newEventBuilder().name("inventoryChanged").data("").build();
        return Multi.createBy().merging().streams(
            Multi.createFrom().item(changed),
            Multi.createFrom().publisher(inventoryChanges.changes()).map(change -> changed),
            Multi.createFrom().ticks().every(HEARTBEAT).map(tick -> sse.newEventBuilder().comment("heartbeat").build())
        );
    }
}
