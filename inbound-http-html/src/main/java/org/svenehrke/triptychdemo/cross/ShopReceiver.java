package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.inventory.InventoryChangesHandler;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseHandler;

import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseOutcome;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.io.InputStream;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Path("/shop")
public class ShopReceiver {

    /** Lets the server notice closed connections (and keeps proxies from dropping an idle one). */
    static final Duration HEARTBEAT = Duration.ofSeconds(15);

    @Inject
    ProductsHandler productsHandler;
    @Inject
    PurchaseHandler purchaseHandler;
    @Inject
    AuditLogHandler auditLog;
    @Inject
    InventoryChangesHandler inventoryChanges;

    /** The static page shell; its {@code #app} element loads {@link #page()} and renders it in the browser. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public InputStream shell() {
        return ShopReceiver.class.getResourceAsStream("/shells/shop.html");
    }

    @GET
    @Path("/page")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse page() {
        return shopPageView(List.of());
    }

    @GET
    @Path("/inventory-fragment")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse inventoryFragment() {
        return UiResponse.of(UiRoute.ShopProducts, new ShopProductsVM(inStockProducts()));
    }

    /**
     * SSE stream for the shop shell: an {@code inventoryChanged} event per inventory change, on which the page
     * re-fetches {@link #inventoryFragment()}. One is also sent on (re)connect, so changes made while the
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

    @POST
    @Path("/checkout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response checkout(@FormParam("productName") List<String> productNames,
                             @FormParam("quantity") List<String> quantities) {
        var cart = ShopCart.of(productNames, quantities);
        logReceived(cart);
        if (cart.isEmpty()) return shopPage(Response.Status.OK, List.of());
        return switch (cart.parse()) {
            case ParsedPurchase.Invalid invalid -> badRequest(cart.errorsOf(invalid));
            case Purchase purchase -> switch (purchaseHandler.checkout(purchase)) {
                case PurchaseOutcome.Rejected rejected -> shopPage(Response.Status.CONFLICT, rejected.messages());
                case PurchaseOutcome.Completed completed -> shopPage(Response.Status.OK, List.of());
            };
        };
    }

    private void logReceived(ShopCart cart) {
        auditLog.log("ShopReceiver: PURCHASE_RECEIVED",
            cart.rows().stream().map(row -> row.name() + " qty=" + row.quantity()).collect(Collectors.joining(", ")));
    }

    private Response badRequest(List<String> errors) {
        return shopPage(Response.Status.BAD_REQUEST, errors);
    }

    private Response shopPage(Response.Status status, List<String> errors) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(shopPageView(errors)).build();
    }

    private UiResponse shopPageView(List<String> errors) {
        return UiResponse.of(UiRoute.ShopPage, new ShopPageVM(inStockProducts(), errors));
    }

    private List<ProductRowVM> inStockProducts() {
        return productsHandler.listAll().stream()
            .filter(p -> p.availableAmount() > 0)
            .map(ProductRowVM::of)
            .sorted(Comparator.comparing(ProductRowVM::name, String.CASE_INSENSITIVE_ORDER).thenComparing(ProductRowVM::type))
            .toList();
    }
}
