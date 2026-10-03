package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentHandler;
import org.svenehrke.triptychdemo.feature.bakery.BakeryHandler;
import org.svenehrke.triptychdemo.feature.bakery.BakeryOrder;
import org.svenehrke.triptychdemo.feature.bakery.ParsedBakeryOrder;
import org.svenehrke.triptychdemo.feature.beverage.BeverageOrder;
import org.svenehrke.triptychdemo.feature.beverage.ParsedBeverageOrder;
import org.svenehrke.triptychdemo.feature.beverage.BeveragesHandler;
import org.svenehrke.triptychdemo.feature.dairy.DairyHandler;
import org.svenehrke.triptychdemo.feature.dairy.DairyOrder;
import org.svenehrke.triptychdemo.feature.dairy.ParsedDairyOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitOrder;
import org.svenehrke.triptychdemo.feature.fruit.ParsedFruitOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitsHandler;
import org.svenehrke.triptychdemo.feature.meat.MeatHandler;
import org.svenehrke.triptychdemo.feature.meat.MeatOrder;
import org.svenehrke.triptychdemo.feature.meat.ParsedMeatOrder;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodHandler;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodOrder;
import org.svenehrke.triptychdemo.feature.nonfood.ParsedNonFoodOrder;
import org.svenehrke.triptychdemo.feature.vegetable.VegetableOrder;
import org.svenehrke.triptychdemo.feature.vegetable.ParsedVegetableOrder;
import org.svenehrke.triptychdemo.feature.vegetable.VegetablesHandler;

import jakarta.validation.ConstraintViolation;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Head office: the stock of every location, supplier orders (for the DC) and the stores' pending requests. */
@Path("/admin")
public class AdminReceiver {

    private static final int AUDIT_LOG_LIMIT = 100;

    @Inject
    ProductsHandler productsHandler;
    @Inject
    FruitsHandler fruitsHandler;
    @Inject
    VegetablesHandler vegetablesHandler;
    @Inject
    DairyHandler dairyHandler;
    @Inject
    BeveragesHandler beveragesHandler;
    @Inject
    MeatHandler meatHandler;
    @Inject
    BakeryHandler bakeryHandler;
    @Inject
    NonFoodHandler nonFoodHandler;
    @Inject
    ReplenishmentHandler replenishmentHandler;
    @Inject
    AuditLogHandler auditLogHandler;

    /** The static page shell; its {@code #app} element loads {@link #page()} and renders it in the browser. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String shell() {
        return PageShell.render("/shells/admin.html", "/admin", Map.of());
    }

    @GET
    @Path("/page")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse page() {
        return UiResponse.of(UiRoute.AdminPage, new AdminPageVM(locations(), products(), pendingRequests(), auditEntries()));
    }

    @GET
    @Path("/inventory-fragment")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse inventoryFragment() {
        return UiResponse.of(UiRoute.AdminInventory, new AdminInventoryVM(locations(), products()));
    }

    @GET
    @Path("/requests-fragment")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse requestsFragment() {
        return UiResponse.of(UiRoute.AdminRequests, new AdminRequestsVM(pendingRequests()));
    }

    /** Serves the request with whatever the DC has, ahead of older ones; the rest stays pending. */
    @POST
    @Path("/requests/{requestId}/fulfil")
    public Response fulfilRequest(@PathParam("requestId") long requestId) {
        auditLogHandler.log("AdminReceiver: FULFIL_RECEIVED", "request " + requestId);
        return replenishmentHandler.fulfil(requestId).isPresent() ? orderAccepted() : notPending(requestId);
    }

    @POST
    @Path("/requests/{requestId}/reject")
    public Response rejectRequest(@PathParam("requestId") long requestId) {
        auditLogHandler.log("AdminReceiver: REJECT_RECEIVED", "request " + requestId);
        return replenishmentHandler.reject(requestId).isPresent() ? orderAccepted() : notPending(requestId);
    }

    @GET
    @Path("/audit-fragment")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse auditFragment() {
        return UiResponse.of(UiRoute.AuditPanel, new AuditPanelVM(auditEntries()));
    }

    @POST
    @Path("/order-fruits")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderFruits(@FormParam("productName") String productName,
                                @FormParam("quantity") String quantity) {
        logReceived("FRUITS_ORDER_RECEIVED", productName, quantity);
        return switch (FruitOrder.parse(productName, quantity)) {
            case ParsedFruitOrder.Invalid invalid -> badRequest(invalid.violations());
            case FruitOrder fruitOrder -> {
                fruitsHandler.order(fruitOrder);
                yield orderAccepted();
            }
        };
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderVegetables(@FormParam("productName") String productName,
                                    @FormParam("quantity") String quantity) {
        logReceived("VEGETABLES_ORDER_RECEIVED", productName, quantity);
        return switch (VegetableOrder.parse(productName, quantity)) {
            case ParsedVegetableOrder.Invalid invalid -> badRequest(invalid.violations());
            case VegetableOrder vegetableOrder -> {
                vegetablesHandler.order(vegetableOrder);
                yield orderAccepted();
            }
        };
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderDairy(@FormParam("productName") String productName,
                               @FormParam("quantity") String quantity) {
        logReceived("DAIRY_ORDER_RECEIVED", productName, quantity);
        return switch (DairyOrder.parse(productName, quantity)) {
            case ParsedDairyOrder.Invalid invalid -> badRequest(invalid.violations());
            case DairyOrder dairyOrder -> {
                dairyHandler.order(dairyOrder);
                yield orderAccepted();
            }
        };
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBeverages(@FormParam("productName") String productName,
                                   @FormParam("quantity") String quantity) {
        logReceived("BEVERAGES_ORDER_RECEIVED", productName, quantity);
        return switch (BeverageOrder.parse(productName, quantity)) {
            case ParsedBeverageOrder.Invalid invalid -> badRequest(invalid.violations());
            case BeverageOrder beverageOrder -> {
                beveragesHandler.order(beverageOrder);
                yield orderAccepted();
            }
        };
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderMeat(@FormParam("productName") String productName,
                              @FormParam("quantity") String quantity) {
        logReceived("MEAT_ORDER_RECEIVED", productName, quantity);
        return switch (MeatOrder.parse(productName, quantity)) {
            case ParsedMeatOrder.Invalid invalid -> badRequest(invalid.violations());
            case MeatOrder meatOrder -> {
                meatHandler.order(meatOrder);
                yield orderAccepted();
            }
        };
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBakery(@FormParam("productName") String productName,
                                @FormParam("quantity") String quantity) {
        logReceived("BAKERY_ORDER_RECEIVED", productName, quantity);
        return switch (BakeryOrder.parse(productName, quantity)) {
            case ParsedBakeryOrder.Invalid invalid -> badRequest(invalid.violations());
            case BakeryOrder bakeryOrder -> {
                bakeryHandler.order(bakeryOrder);
                yield orderAccepted();
            }
        };
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderNonFood(@FormParam("productName") String productName,
                                 @FormParam("quantity") String quantity) {
        logReceived("NONFOOD_ORDER_RECEIVED", productName, quantity);
        return switch (NonFoodOrder.parse(productName, quantity)) {
            case ParsedNonFoodOrder.Invalid invalid -> badRequest(invalid.violations());
            case NonFoodOrder nonFoodOrder -> {
                nonFoodHandler.order(nonFoodOrder);
                yield orderAccepted();
            }
        };
    }

    private void logReceived(String event, String productName, String quantity) {
        auditLogHandler.log("AdminReceiver: " + event, productName + " qty=" + quantity);
    }

    private static Response badRequest(Set<? extends ConstraintViolation<?>> violations) {
        return UiResponse.response(Response.Status.BAD_REQUEST, UiRoute.OrderErrors,
            new OrderErrorsVM(violations.stream().map(ConstraintViolation::getMessage).toList()));
    }

    private static Response notPending(long requestId) {
        return UiResponse.response(Response.Status.CONFLICT, UiRoute.OrderErrors,
            new OrderErrorsVM(List.of("request " + requestId + " is no longer pending")));
    }

    private static Response orderAccepted() {
        // 200 with an empty body (not 204, which htmx never swaps) clears a previous error below the form
        return Response.ok("", MediaType.TEXT_HTML).build();
    }

    private static List<LocationVM> locations() {
        return Locations.ALL.stream().map(LocationVM::of).toList();
    }

    private List<StockRowVM> products() {
        return productsHandler.listAllLocations().stream().map(p -> StockRowVM.of(p, Locations.ALL))
            .sorted(Comparator.comparing(StockRowVM::name, String.CASE_INSENSITIVE_ORDER).thenComparing(StockRowVM::type))
            .toList();
    }

    private List<RequestVM> pendingRequests() {
        return replenishmentHandler.listPending().stream().map(RequestVM::of).toList();
    }

    private List<AuditEntryVM> auditEntries() {
        return auditLogHandler.recent(AUDIT_LOG_LIMIT).stream().map(AuditEntryVM::of).toList();
    }
}
