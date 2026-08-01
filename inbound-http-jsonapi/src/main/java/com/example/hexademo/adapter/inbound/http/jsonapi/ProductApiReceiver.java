package com.example.hexademo.adapter.inbound.http.jsonapi;

import com.example.hexademo.adapter.inbound.http.jsonapi.cashpoint.PurchaseRequest;
import com.example.hexademo.core.application.APIs;
import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.PurchaseItem;
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

    @Inject
    APIs.ProductsAPI productsAPI;
    @Inject
    APIs.FruitsAPI fruitsAPI;
    @Inject
    APIs.VegetablesAPI vegetablesAPI;
    @Inject
    APIs.DairyAPI dairyAPI;
    @Inject
    APIs.BeveragesAPI beveragesAPI;
    @Inject
    APIs.MeatAPI meatAPI;
    @Inject
    APIs.BakeryAPI bakeryAPI;
    @Inject
    APIs.NonFoodAPI nonFoodAPI;
    @Inject
    APIs.PurchaseAPI purchaseAPI;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> list() {
        return productsAPI.listAll();
    }

    @POST
    @Path("/order-fruits")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderFruits(com.example.hexademo.adapter.inbound.http.jsonapi.fruit.OrderRequest request) {
        fruitsAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderVegetables(com.example.hexademo.adapter.inbound.http.jsonapi.vegetable.OrderRequest request) {
        vegetablesAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderDairy(com.example.hexademo.adapter.inbound.http.jsonapi.dairy.OrderRequest request) {
        dairyAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderBeverages(com.example.hexademo.adapter.inbound.http.jsonapi.beverage.OrderRequest request) {
        beveragesAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderMeat(com.example.hexademo.adapter.inbound.http.jsonapi.meat.OrderRequest request) {
        meatAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderBakery(com.example.hexademo.adapter.inbound.http.jsonapi.bakery.OrderRequest request) {
        bakeryAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderNonFood(com.example.hexademo.adapter.inbound.http.jsonapi.nonfood.OrderRequest request) {
        nonFoodAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/purchase")
    @Consumes(MediaType.APPLICATION_JSON)
    public void purchase(PurchaseRequest request) {
        var items = request.items().stream()
            .map(i -> new PurchaseItem(i.productName(), i.quantity()))
            .toList();
        purchaseAPI.purchase(items);
    }
}
