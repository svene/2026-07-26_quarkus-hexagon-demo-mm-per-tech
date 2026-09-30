package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseHandler;

import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseOutcome;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Path("/shop")
public class ShopReceiver {

    @Inject
    ProductsHandler productsHandler;
    @Inject
    PurchaseHandler purchaseHandler;
    @Inject
    AuditLogHandler auditLog;

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
        return shopPageModel(List.of());
    }

    @GET
    @Path("/inventory-fragment")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse inventoryFragment() {
        return UiResponse.of(UiRoute.ShopAvailability,
            new ShopAvailabilityModel(productsHandler.listAll().stream().map(ProductRowModel::of).toList()));
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
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(shopPageModel(errors)).build();
    }

    private UiResponse shopPageModel(List<String> errors) {
        var inStock = productsHandler.listAll().stream()
            .filter(p -> p.availableAmount() > 0)
            .map(ProductRowModel::of)
            .toList();
        return UiResponse.of(UiRoute.ShopPage, new ShopPageModel(inStock, errors));
    }
}
