package com.example.hexademo.adapter.inbound.rest;

import com.example.hexademo.adapter.inbound.rest.cashpoint.PurchaseRequest;
import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.port.in.BakeryAPI;
import com.example.hexademo.core.port.in.BeveragesAPI;
import com.example.hexademo.core.port.in.DairyAPI;
import com.example.hexademo.core.port.in.FruitsAPI;
import com.example.hexademo.core.port.in.MeatAPI;
import com.example.hexademo.core.port.in.NonFoodAPI;
import com.example.hexademo.core.port.in.ProductsAPI;
import com.example.hexademo.core.port.in.PurchaseAPI;
import com.example.hexademo.core.port.in.VegetablesAPI;
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
    @Inject VegetablesAPI vegetablesAPI;
    @Inject DairyAPI dairyAPI;
    @Inject BeveragesAPI beveragesAPI;
    @Inject MeatAPI meatAPI;
    @Inject BakeryAPI bakeryAPI;
    @Inject NonFoodAPI nonFoodAPI;
    @Inject PurchaseAPI purchaseAPI;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> list() {
        return productsAPI.listAll();
    }

    @POST
    @Path("/order-fruits")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderFruits(com.example.hexademo.adapter.inbound.rest.fruit.OrderRequest request) {
        fruitsAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderVegetables(com.example.hexademo.adapter.inbound.rest.vegetable.OrderRequest request) {
        vegetablesAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderDairy(com.example.hexademo.adapter.inbound.rest.dairy.OrderRequest request) {
        dairyAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderBeverages(com.example.hexademo.adapter.inbound.rest.beverage.OrderRequest request) {
        beveragesAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderMeat(com.example.hexademo.adapter.inbound.rest.meat.OrderRequest request) {
        meatAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderBakery(com.example.hexademo.adapter.inbound.rest.bakery.OrderRequest request) {
        bakeryAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderNonFood(com.example.hexademo.adapter.inbound.rest.nonfood.OrderRequest request) {
        nonFoodAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/purchase")
    @Consumes(MediaType.APPLICATION_JSON)
    public void purchase(PurchaseRequest request) {
        purchaseAPI.purchase(request.productName(), request.quantity());
    }
}
