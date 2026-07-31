package com.example.hexademo.adapter.inbound.rest;

import com.example.hexademo.core.domain.AuditLogEntry;
import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.port.in.AuditLogAPI;
import com.example.hexademo.core.port.in.BakeryAPI;
import com.example.hexademo.core.port.in.BeveragesAPI;
import com.example.hexademo.core.port.in.DairyAPI;
import com.example.hexademo.core.port.in.FruitsAPI;
import com.example.hexademo.core.port.in.MeatAPI;
import com.example.hexademo.core.port.in.NonFoodAPI;
import com.example.hexademo.core.port.in.ProductsAPI;
import com.example.hexademo.core.port.in.VegetablesAPI;
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

@Path("/admin")
public class AdminReceiver {

    private static final int AUDIT_LOG_LIMIT = 100;

    @Inject ProductsAPI productsAPI;
    @Inject FruitsAPI fruitsAPI;
    @Inject VegetablesAPI vegetablesAPI;
    @Inject DairyAPI dairyAPI;
    @Inject BeveragesAPI beveragesAPI;
    @Inject MeatAPI meatAPI;
    @Inject BakeryAPI bakeryAPI;
    @Inject NonFoodAPI nonFoodAPI;
    @Inject AuditLogAPI auditLogAPI;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance admin(List<Product> products);
        public static native TemplateInstance audit(List<AuditLogEntry> entries);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        return Templates.admin(productsAPI.listAll());
    }

    @GET
    @Path("/audit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance audit() {
        return Templates.audit(auditLogAPI.recent(AUDIT_LOG_LIMIT));
    }

    @POST
    @Path("/order-fruits")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderFruits(@FormParam("productName") String productName,
                                @FormParam("quantity") int quantity) {
        fruitsAPI.order(productName, quantity);
        return Response.seeOther(URI.create("/admin")).build();
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderVegetables(@FormParam("productName") String productName,
                                    @FormParam("quantity") int quantity) {
        vegetablesAPI.order(productName, quantity);
        return Response.seeOther(URI.create("/admin")).build();
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderDairy(@FormParam("productName") String productName,
                               @FormParam("quantity") int quantity) {
        dairyAPI.order(productName, quantity);
        return Response.seeOther(URI.create("/admin")).build();
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBeverages(@FormParam("productName") String productName,
                                   @FormParam("quantity") int quantity) {
        beveragesAPI.order(productName, quantity);
        return Response.seeOther(URI.create("/admin")).build();
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderMeat(@FormParam("productName") String productName,
                              @FormParam("quantity") int quantity) {
        meatAPI.order(productName, quantity);
        return Response.seeOther(URI.create("/admin")).build();
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBakery(@FormParam("productName") String productName,
                                @FormParam("quantity") int quantity) {
        bakeryAPI.order(productName, quantity);
        return Response.seeOther(URI.create("/admin")).build();
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderNonFood(@FormParam("productName") String productName,
                                 @FormParam("quantity") int quantity) {
        nonFoodAPI.order(productName, quantity);
        return Response.seeOther(URI.create("/admin")).build();
    }
}
