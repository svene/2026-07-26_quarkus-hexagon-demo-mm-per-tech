package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.products.ProductsAPI;
import org.svenehrke.triptychdemo.feature.bakery.BakeryAPI;
import org.svenehrke.triptychdemo.feature.bakery.BakeryOrder;
import org.svenehrke.triptychdemo.feature.bakery.ParsedBakeryOrder;
import org.svenehrke.triptychdemo.feature.beverage.BeverageOrder;
import org.svenehrke.triptychdemo.feature.beverage.ParsedBeverageOrder;
import org.svenehrke.triptychdemo.feature.beverage.BeveragesAPI;
import org.svenehrke.triptychdemo.feature.dairy.DairyAPI;
import org.svenehrke.triptychdemo.feature.dairy.DairyOrder;
import org.svenehrke.triptychdemo.feature.dairy.ParsedDairyOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitOrder;
import org.svenehrke.triptychdemo.feature.fruit.ParsedFruitOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitsAPI;
import org.svenehrke.triptychdemo.feature.meat.MeatAPI;
import org.svenehrke.triptychdemo.feature.meat.MeatOrder;
import org.svenehrke.triptychdemo.feature.meat.ParsedMeatOrder;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodAPI;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodOrder;
import org.svenehrke.triptychdemo.feature.nonfood.ParsedNonFoodOrder;
import org.svenehrke.triptychdemo.feature.vegetable.VegetableOrder;
import org.svenehrke.triptychdemo.feature.vegetable.ParsedVegetableOrder;
import org.svenehrke.triptychdemo.feature.vegetable.VegetablesAPI;

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
    ProductsAPI productsAPI;
    @Inject
    FruitsAPI fruitsAPI;
    @Inject
    VegetablesAPI vegetablesAPI;
    @Inject
    DairyAPI dairyAPI;
    @Inject
    BeveragesAPI beveragesAPI;
    @Inject
    MeatAPI meatAPI;
    @Inject
    BakeryAPI bakeryAPI;
    @Inject
    NonFoodAPI nonFoodAPI;
    @Inject
    AuditLogAPI auditLogAPI;

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
        return Templates.admin(productsAPI.listAll(), auditLogAPI.recent(AUDIT_LOG_LIMIT));
    }

    @GET
    @Path("/inventory-fragment")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance inventoryFragment() {
        return Templates.inventoryFragment(productsAPI.listAll());
    }

    @GET
    @Path("/audit-fragment")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance auditFragment() {
        return Templates.auditFragment(auditLogAPI.recent(AUDIT_LOG_LIMIT));
    }

    @POST
    @Path("/order-fruits")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderFruits(@FormParam("productName") String productName,
                                @FormParam("quantity") int quantity,
                                @HeaderParam("HX-Request") String hxRequest) {
        return switch (FruitOrder.parse(productName, quantity)) {
            case ParsedFruitOrder.Invalid invalid -> badRequest(invalid.violations());
            case FruitOrder fruitOrder -> {
                fruitsAPI.order(fruitOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderVegetables(@FormParam("productName") String productName,
                                    @FormParam("quantity") int quantity,
                                    @HeaderParam("HX-Request") String hxRequest) {
        return switch (VegetableOrder.parse(productName, quantity)) {
            case ParsedVegetableOrder.Invalid invalid -> badRequest(invalid.violations());
            case VegetableOrder vegetableOrder -> {
                vegetablesAPI.order(vegetableOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderDairy(@FormParam("productName") String productName,
                               @FormParam("quantity") int quantity,
                               @HeaderParam("HX-Request") String hxRequest) {
        return switch (DairyOrder.parse(productName, quantity)) {
            case ParsedDairyOrder.Invalid invalid -> badRequest(invalid.violations());
            case DairyOrder dairyOrder -> {
                dairyAPI.order(dairyOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBeverages(@FormParam("productName") String productName,
                                   @FormParam("quantity") int quantity,
                                   @HeaderParam("HX-Request") String hxRequest) {
        return switch (BeverageOrder.parse(productName, quantity)) {
            case ParsedBeverageOrder.Invalid invalid -> badRequest(invalid.violations());
            case BeverageOrder beverageOrder -> {
                beveragesAPI.order(beverageOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderMeat(@FormParam("productName") String productName,
                              @FormParam("quantity") int quantity,
                              @HeaderParam("HX-Request") String hxRequest) {
        return switch (MeatOrder.parse(productName, quantity)) {
            case ParsedMeatOrder.Invalid invalid -> badRequest(invalid.violations());
            case MeatOrder meatOrder -> {
                meatAPI.order(meatOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBakery(@FormParam("productName") String productName,
                                @FormParam("quantity") int quantity,
                                @HeaderParam("HX-Request") String hxRequest) {
        return switch (BakeryOrder.parse(productName, quantity)) {
            case ParsedBakeryOrder.Invalid invalid -> badRequest(invalid.violations());
            case BakeryOrder bakeryOrder -> {
                bakeryAPI.order(bakeryOrder);
                yield orderResponse(hxRequest);
            }
        };
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderNonFood(@FormParam("productName") String productName,
                                 @FormParam("quantity") int quantity,
                                 @HeaderParam("HX-Request") String hxRequest) {
        return switch (NonFoodOrder.parse(productName, quantity)) {
            case ParsedNonFoodOrder.Invalid invalid -> badRequest(invalid.violations());
            case NonFoodOrder nonFoodOrder -> {
                nonFoodAPI.order(nonFoodOrder);
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
