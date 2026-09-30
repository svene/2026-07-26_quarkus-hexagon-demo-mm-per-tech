package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseHandler;

import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;
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
import java.util.ArrayList;
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
        var rows = cartRows(productNames, quantities);
        logReceived(rows);
        // Cart semantics (UI concern, not validation): a row with a 0 quantity is simply not in the cart.
        var cart = rows.stream().filter(row -> !isZero(row.quantity())).toList();
        if (cart.isEmpty()) return redirectToShop();
        return switch (Purchase.parse(cart.stream().map(row -> PurchaseItem.parse(row.name(), row.quantity())).toList())) {
            case ParsedPurchase.Invalid invalid -> badRequest(errorsOf(invalid, cart));
            case Purchase purchase -> switch (purchaseHandler.checkout(purchase)) {
                case PurchaseOutcome.Rejected rejected -> shopPage(Response.Status.CONFLICT, rejected.messages());
                case PurchaseOutcome.Completed completed -> redirectToShop();
            };
        };
    }

    private record CartRow(String name, String quantity) {}

    // Only rows with a quantity entered: the form submits a row for every product in stock.
    private static List<CartRow> cartRows(List<String> productNames, List<String> quantities) {
        var rows = new ArrayList<CartRow>();
        if (productNames != null) {
            for (int i = 0; i < productNames.size(); i++) {
                var quantity = quantities != null && i < quantities.size() ? quantities.get(i) : null;
                if (quantity != null && !quantity.isBlank()) rows.add(new CartRow(productNames.get(i), quantity));
            }
        }
        return rows;
    }

    private static boolean isZero(String quantity) {
        return quantity.trim().matches("[+-]?0+");
    }

    private void logReceived(List<CartRow> rows) {
        auditLog.log("ShopReceiver: PURCHASE_RECEIVED",
            rows.stream().map(row -> row.name() + " qty=" + row.quantity()).collect(Collectors.joining(", ")));
    }

    // Prefixed with the product name rather than items[i]: a shop user can't map an index to a row.
    private static List<String> errorsOf(ParsedPurchase.Invalid invalid, List<CartRow> cart) {
        return invalid.violationsByItemIndex().entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(v -> cart.get(e.getKey()).name() + ": " + v.getMessage()))
            .toList();
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
