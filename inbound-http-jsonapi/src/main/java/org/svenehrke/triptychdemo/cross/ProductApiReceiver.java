package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.products.ProductsAPI;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseAPI;
import org.svenehrke.triptychdemo.feature.bakery.BakeryAPI;
import org.svenehrke.triptychdemo.feature.beverage.BeveragesAPI;
import org.svenehrke.triptychdemo.feature.dairy.DairyAPI;
import org.svenehrke.triptychdemo.feature.fruit.FruitsAPI;
import org.svenehrke.triptychdemo.feature.meat.MeatAPI;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodAPI;
import org.svenehrke.triptychdemo.feature.vegetable.VegetablesAPI;

import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;
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
    PurchaseAPI purchaseAPI;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> list() {
        return productsAPI.listAll();
    }

    @POST
    @Path("/order-fruits")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderFruits(Requests.FruitOrderRequest request) {
        fruitsAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderVegetables(Requests.VegetableOrderRequest request) {
        vegetablesAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderDairy(Requests.DairyOrderRequest request) {
        dairyAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderBeverages(Requests.BeverageOrderRequest request) {
        beveragesAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderMeat(Requests.MeatOrderRequest request) {
        meatAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderBakery(Requests.BakeryOrderRequest request) {
        bakeryAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_JSON)
    public void orderNonFood(Requests.NonFoodOrderRequest request) {
        nonFoodAPI.order(request.productName(), request.quantity());
    }

    @POST
    @Path("/purchase")
    @Consumes(MediaType.APPLICATION_JSON)
    public void purchase(Requests.PurchaseRequest request) {
        var items = request.items().stream()
            .map(i -> new PurchaseItem(i.productName(), i.quantity()))
            .toList();
        purchaseAPI.purchase(items);
    }
}
