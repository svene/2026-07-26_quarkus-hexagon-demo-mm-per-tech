package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.products.ProductsAPI;
import org.svenehrke.triptychdemo.feature.bakery.BakeryAPI;
import org.svenehrke.triptychdemo.feature.bakery.BakeryOrder;
import org.svenehrke.triptychdemo.feature.beverage.BeverageOrder;
import org.svenehrke.triptychdemo.feature.beverage.BeveragesAPI;
import org.svenehrke.triptychdemo.feature.dairy.DairyAPI;
import org.svenehrke.triptychdemo.feature.dairy.DairyOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitsAPI;
import org.svenehrke.triptychdemo.feature.meat.MeatAPI;
import org.svenehrke.triptychdemo.feature.meat.MeatOrder;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodAPI;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodOrder;
import org.svenehrke.triptychdemo.feature.vegetable.VegetableOrder;
import org.svenehrke.triptychdemo.feature.vegetable.VegetablesAPI;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogEntry;
import org.svenehrke.triptychdemo.cross.products.Product;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
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
        fruitsAPI.order(new FruitOrder(productName, quantity));
        return orderResponse(hxRequest);
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderVegetables(@FormParam("productName") String productName,
                                    @FormParam("quantity") int quantity,
                                    @HeaderParam("HX-Request") String hxRequest) {
        vegetablesAPI.order(new VegetableOrder(productName, quantity));
        return orderResponse(hxRequest);
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderDairy(@FormParam("productName") String productName,
                               @FormParam("quantity") int quantity,
                               @HeaderParam("HX-Request") String hxRequest) {
        dairyAPI.order(new DairyOrder(productName, quantity));
        return orderResponse(hxRequest);
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBeverages(@FormParam("productName") String productName,
                                   @FormParam("quantity") int quantity,
                                   @HeaderParam("HX-Request") String hxRequest) {
        beveragesAPI.order(new BeverageOrder(productName, quantity));
        return orderResponse(hxRequest);
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderMeat(@FormParam("productName") String productName,
                              @FormParam("quantity") int quantity,
                              @HeaderParam("HX-Request") String hxRequest) {
        meatAPI.order(new MeatOrder(productName, quantity));
        return orderResponse(hxRequest);
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBakery(@FormParam("productName") String productName,
                                @FormParam("quantity") int quantity,
                                @HeaderParam("HX-Request") String hxRequest) {
        bakeryAPI.order(new BakeryOrder(productName, quantity));
        return orderResponse(hxRequest);
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderNonFood(@FormParam("productName") String productName,
                                 @FormParam("quantity") int quantity,
                                 @HeaderParam("HX-Request") String hxRequest) {
        nonFoodAPI.order(new NonFoodOrder(productName, quantity));
        return orderResponse(hxRequest);
    }

    private Response orderResponse(String hxRequest) {
        if ("true".equals(hxRequest)) {
            return Response.noContent().build();
        }
        return Response.seeOther(URI.create("/admin")).build();
    }
}
