package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.products.ProductsHandler;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseHandler;

import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchaseItem;
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

@Path("/shop")
public class ShopReceiver {

    @Inject
    ProductsHandler productsHandler;
    @Inject
    PurchaseHandler purchaseHandler;

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
        // Cart semantics (UI concern, not validation): a row with a blank or 0 quantity is simply not in the cart.
        var errors = new ArrayList<String>();
        var cartNames = new ArrayList<String>();
        var parsedItems = new ArrayList<ParsedPurchaseItem>();
        if (productNames != null) {
            for (int i = 0; i < productNames.size(); i++) {
                var name = productNames.get(i);
                var qtyStr = quantities != null && i < quantities.size() ? quantities.get(i) : null;
                if (qtyStr == null || qtyStr.isBlank()) continue;
                int qty;
                try {
                    qty = Integer.parseInt(qtyStr.trim());
                } catch (NumberFormatException e) {
                    errors.add(name + ": quantity must be a number");
                    continue;
                }
                if (qty == 0) continue;
                cartNames.add(name);
                parsedItems.add(PurchaseItem.parse(name, qty));
            }
        }
        return switch (Purchase.parse(parsedItems)) {
            case ParsedPurchase.Invalid invalid -> {
                invalid.violationsByItemIndex().forEach((index, violations) ->
                    violations.forEach(v -> errors.add(cartNames.get(index) + ": " + v.getMessage())));
                yield badRequest(errors);
            }
            case Purchase purchase -> {
                if (!errors.isEmpty()) yield badRequest(errors);
                if (purchase.items().isEmpty()) yield Response.seeOther(URI.create("/shop")).build();
                yield switch (purchaseHandler.checkout(purchase)) {
                    case PurchaseOutcome.Rejected rejected -> shopPage(Response.Status.CONFLICT, rejected.messages());
                    case PurchaseOutcome.Completed completed -> Response.seeOther(URI.create("/shop")).build();
                };
            }
        };
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
