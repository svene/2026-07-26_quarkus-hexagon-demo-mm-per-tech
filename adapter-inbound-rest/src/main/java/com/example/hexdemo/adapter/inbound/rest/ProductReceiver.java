package com.example.hexdemo.adapter.inbound.rest;

import com.example.hexdemo.core.domain.Product;
import com.example.hexdemo.core.port.in.BeveragesAPI;
import com.example.hexdemo.core.port.in.FruitsAPI;
import com.example.hexdemo.core.port.in.ProductsAPI;
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

@Path("/products")
public class ProductReceiver {

    @Inject ProductsAPI productsAPI;
    @Inject FruitsAPI fruitsAPI;
    @Inject BeveragesAPI beveragesAPI;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance products(List<Product> products);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        return Templates.products(productsAPI.listAll());
    }

    @POST
    @Path("/order-fruits")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderFruits(@FormParam("productName") String productName,
                                @FormParam("quantity") int quantity) {
        fruitsAPI.order(productName, quantity);
        return Response.seeOther(URI.create("/products")).build();
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response orderBeverages(@FormParam("productName") String productName,
                                   @FormParam("quantity") int quantity) {
        beveragesAPI.order(productName, quantity);
        return Response.seeOther(URI.create("/products")).build();
    }
}
