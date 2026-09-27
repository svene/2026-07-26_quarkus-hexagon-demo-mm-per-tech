package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;
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

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogEntry;
import org.svenehrke.triptychdemo.cross.products.Product;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.validation.ConstraintViolation;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Set;

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
    AuditLogHandler auditLogHandler;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance admin(List<Product> products, List<AuditLogEntry> auditEntries);
        public static native TemplateInstance inventoryFragment(List<Product> products);
        public static native TemplateInstance auditFragment(List<AuditLogEntry> auditEntries);
        public static native TemplateInstance orderErrors(List<String> messages);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        return Templates.admin(productsHandler.listAll(), auditLogHandler.recent(AUDIT_LOG_LIMIT));
    }

    @GET
    @Path("/inventory-fragment")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance inventoryFragment() {
        return Templates.inventoryFragment(productsHandler.listAll());
    }

    @GET
    @Path("/audit-fragment")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance auditFragment() {
        return Templates.auditFragment(auditLogHandler.recent(AUDIT_LOG_LIMIT));
    }

    @POST
    @Path("/order-fruits")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderFruits(@FormParam("productName") String productName,
                                @FormParam("quantity") String quantity,
                                @HeaderParam("HX-Request") String hxRequest) {
        return switch (FruitOrder.parse(productName, quantity)) {
            case ParsedFruitOrder.Invalid invalid -> badRequest(invalid.violations());
            case FruitOrder fruitOrder -> {
                fruitsHandler.order(fruitOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderVegetables(@FormParam("productName") String productName,
                                    @FormParam("quantity") String quantity,
                                    @HeaderParam("HX-Request") String hxRequest) {
        return switch (VegetableOrder.parse(productName, quantity)) {
            case ParsedVegetableOrder.Invalid invalid -> badRequest(invalid.violations());
            case VegetableOrder vegetableOrder -> {
                vegetablesHandler.order(vegetableOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderDairy(@FormParam("productName") String productName,
                               @FormParam("quantity") String quantity,
                               @HeaderParam("HX-Request") String hxRequest) {
        return switch (DairyOrder.parse(productName, quantity)) {
            case ParsedDairyOrder.Invalid invalid -> badRequest(invalid.violations());
            case DairyOrder dairyOrder -> {
                dairyHandler.order(dairyOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBeverages(@FormParam("productName") String productName,
                                   @FormParam("quantity") String quantity,
                                   @HeaderParam("HX-Request") String hxRequest) {
        return switch (BeverageOrder.parse(productName, quantity)) {
            case ParsedBeverageOrder.Invalid invalid -> badRequest(invalid.violations());
            case BeverageOrder beverageOrder -> {
                beveragesHandler.order(beverageOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderMeat(@FormParam("productName") String productName,
                              @FormParam("quantity") String quantity,
                              @HeaderParam("HX-Request") String hxRequest) {
        return switch (MeatOrder.parse(productName, quantity)) {
            case ParsedMeatOrder.Invalid invalid -> badRequest(invalid.violations());
            case MeatOrder meatOrder -> {
                meatHandler.order(meatOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBakery(@FormParam("productName") String productName,
                                @FormParam("quantity") String quantity,
                                @HeaderParam("HX-Request") String hxRequest) {
        return switch (BakeryOrder.parse(productName, quantity)) {
            case ParsedBakeryOrder.Invalid invalid -> badRequest(invalid.violations());
            case BakeryOrder bakeryOrder -> {
                bakeryHandler.order(bakeryOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderNonFood(@FormParam("productName") String productName,
                                 @FormParam("quantity") String quantity,
                                 @HeaderParam("HX-Request") String hxRequest) {
        return switch (NonFoodOrder.parse(productName, quantity)) {
            case ParsedNonFoodOrder.Invalid invalid -> badRequest(invalid.violations());
            case NonFoodOrder nonFoodOrder -> {
                nonFoodHandler.order(nonFoodOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    private static Response badRequest(Set<? extends ConstraintViolation<?>> violations) {
        return Response.status(Response.Status.BAD_REQUEST)
            .type(MediaType.TEXT_HTML)
            .entity(Templates.orderErrors(violations.stream().map(ConstraintViolation::getMessage).toList()))
            .build();
    }

    private Response orderResponse(String hxRequest) {
        if ("true".equals(hxRequest)) {
            // 200 with an empty body (not 204, which htmx never swaps) clears a previous error below the form
            return Response.ok("", MediaType.TEXT_HTML).build();
        }
        return Response.seeOther(URI.create("/admin")).build();
    }
}
