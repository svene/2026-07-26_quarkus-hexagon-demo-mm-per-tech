package com.example.hexdemo.adapter.inbound.rest;

import com.example.hexdemo.core.domain.Product;
import com.example.hexdemo.core.port.in.BeveragesAPI;
import com.example.hexdemo.core.port.in.FruitsAPI;
import com.example.hexdemo.core.port.in.ProductsAPI;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/products")
public class ProductApiReceiver {

    @Inject ProductsAPI productsAPI;
    @Inject FruitsAPI fruitsAPI;
    @Inject BeveragesAPI beveragesAPI;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> list() {
        return productsAPI.listAll();
    }

    @POST
    @Path("/order-fruits")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderFruits(OrderRequest request) {
        fruitsAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderBeverages(OrderRequest request) {
        beveragesAPI.order(request.productName(), request.quantity());
    }

    public record OrderRequest(String productName, int quantity) {}
}
