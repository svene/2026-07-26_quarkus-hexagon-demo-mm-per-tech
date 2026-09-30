package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseHandler;

import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseOutcome;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
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

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance shop(List<Product> products, List<String> errors);
        public static native TemplateInstance inventoryFragment(List<Product> products);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        return Templates.shop(inStock(), List.of());
    }

    @GET
    @Path("/inventory-fragment")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance inventoryFragment() {
        return Templates.inventoryFragment(productsHandler.listAll());
    }

    @POST
    @Path("/checkout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response checkout(@FormParam("productName") List<String> productNames,
                             @FormParam("quantity") List<String> quantities) {
        var cart = ShopCart.of(productNames, quantities);
        logReceived(cart);
        if (cart.isEmpty()) return redirectToShop();
        return switch (cart.parse()) {
            case ParsedPurchase.Invalid invalid -> badRequest(cart.errorsOf(invalid));
            case Purchase purchase -> switch (purchaseHandler.checkout(purchase)) {
                case PurchaseOutcome.Rejected rejected -> shopPage(Response.Status.CONFLICT, rejected.messages());
                case PurchaseOutcome.Completed completed -> redirectToShop();
            };
        };
    }

    private void logReceived(ShopCart cart) {
        auditLog.log("ShopReceiver: PURCHASE_RECEIVED",
            cart.rows().stream().map(row -> row.name() + " qty=" + row.quantity()).collect(Collectors.joining(", ")));
    }

    private static Response redirectToShop() {
        return Response.seeOther(URI.create("/shop")).build();
    }

    private Response badRequest(List<String> errors) {
        return shopPage(Response.Status.BAD_REQUEST, errors);
    }

    private Response shopPage(Response.Status status, List<String> errors) {
        return Response.status(status)
            .type(MediaType.TEXT_HTML)
            .entity(Templates.shop(inStock(), errors))
            .build();
    }

    private List<Product> inStock() {
        return productsHandler.listAll().stream()
            .filter(p -> p.availableAmount() > 0)
            .toList();
    }
}
