package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.products.ProductsAPI;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseAPI;
import org.svenehrke.triptychdemo.feature.bakery.BakeryAPI;
import org.svenehrke.triptychdemo.feature.bakery.BakeryOrder;
import org.svenehrke.triptychdemo.feature.bakery.ParsedBakeryOrder;
import org.svenehrke.triptychdemo.feature.beverage.BeverageOrder;
import org.svenehrke.triptychdemo.feature.beverage.BeveragesAPI;
import org.svenehrke.triptychdemo.feature.beverage.ParsedBeverageOrder;
import org.svenehrke.triptychdemo.feature.dairy.DairyAPI;
import org.svenehrke.triptychdemo.feature.dairy.DairyOrder;
import org.svenehrke.triptychdemo.feature.dairy.ParsedDairyOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitsAPI;
import org.svenehrke.triptychdemo.feature.fruit.ParsedFruitOrder;
import org.svenehrke.triptychdemo.feature.meat.MeatAPI;
import org.svenehrke.triptychdemo.feature.meat.MeatOrder;
import org.svenehrke.triptychdemo.feature.meat.ParsedMeatOrder;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodAPI;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodOrder;
import org.svenehrke.triptychdemo.feature.nonfood.ParsedNonFoodOrder;
import org.svenehrke.triptychdemo.feature.vegetable.ParsedVegetableOrder;
import org.svenehrke.triptychdemo.feature.vegetable.VegetableOrder;
import org.svenehrke.triptychdemo.feature.vegetable.VegetablesAPI;

import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.IntStream;

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
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderFruits(Requests.FruitOrderRequest request) {
        if (request == null) return badRequest(List.of("request body is required"));
        return switch (FruitOrder.parse(request.productName(), request.quantity())) {
            case ParsedFruitOrder.Invalid invalid -> badRequest(invalid.violations().stream().map(ConstraintViolation::getMessage).toList());
            case FruitOrder fruitOrder -> {
                fruitsAPI.order(fruitOrder);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderVegetables(Requests.VegetableOrderRequest request) {
        if (request == null) return badRequest(List.of("request body is required"));
        return switch (VegetableOrder.parse(request.productName(), request.quantity())) {
            case ParsedVegetableOrder.Invalid invalid -> badRequest(invalid.violations().stream().map(ConstraintViolation::getMessage).toList());
            case VegetableOrder vegetableOrder -> {
                vegetablesAPI.order(vegetableOrder);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderDairy(Requests.DairyOrderRequest request) {
        if (request == null) return badRequest(List.of("request body is required"));
        return switch (DairyOrder.parse(request.productName(), request.quantity())) {
            case ParsedDairyOrder.Invalid invalid -> badRequest(invalid.violations().stream().map(ConstraintViolation::getMessage).toList());
            case DairyOrder dairyOrder -> {
                dairyAPI.order(dairyOrder);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderBeverages(Requests.BeverageOrderRequest request) {
        if (request == null) return badRequest(List.of("request body is required"));
        return switch (BeverageOrder.parse(request.productName(), request.quantity())) {
            case ParsedBeverageOrder.Invalid invalid -> badRequest(invalid.violations().stream().map(ConstraintViolation::getMessage).toList());
            case BeverageOrder beverageOrder -> {
                beveragesAPI.order(beverageOrder);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderMeat(Requests.MeatOrderRequest request) {
        if (request == null) return badRequest(List.of("request body is required"));
        return switch (MeatOrder.parse(request.productName(), request.quantity())) {
            case ParsedMeatOrder.Invalid invalid -> badRequest(invalid.violations().stream().map(ConstraintViolation::getMessage).toList());
            case MeatOrder meatOrder -> {
                meatAPI.order(meatOrder);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderBakery(Requests.BakeryOrderRequest request) {
        if (request == null) return badRequest(List.of("request body is required"));
        return switch (BakeryOrder.parse(request.productName(), request.quantity())) {
            case ParsedBakeryOrder.Invalid invalid -> badRequest(invalid.violations().stream().map(ConstraintViolation::getMessage).toList());
            case BakeryOrder bakeryOrder -> {
                bakeryAPI.order(bakeryOrder);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderNonFood(Requests.NonFoodOrderRequest request) {
        if (request == null) return badRequest(List.of("request body is required"));
        return switch (NonFoodOrder.parse(request.productName(), request.quantity())) {
            case ParsedNonFoodOrder.Invalid invalid -> badRequest(invalid.violations().stream().map(ConstraintViolation::getMessage).toList());
            case NonFoodOrder nonFoodOrder -> {
                nonFoodAPI.order(nonFoodOrder);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/purchase")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response purchase(Requests.PurchaseRequest request) {
        // Request structure first - a missing body/list/entry is a malformed request, not an invalid value
        if (request == null) return badRequest(List.of("request body is required"));
        if (request.items() == null) return badRequest(List.of("items is required"));
        var nullEntries = IntStream.range(0, request.items().size())
            .filter(i -> request.items().get(i) == null)
            .mapToObj(i -> "items[" + i + "]: must not be null")
            .toList();
        if (!nullEntries.isEmpty()) return badRequest(nullEntries);

        var parsedItems = request.items().stream()
            .map(i -> PurchaseItem.parse(i.productName(), i.quantity()))
            .toList();
        return switch (Purchase.parse(parsedItems)) {
            case ParsedPurchase.Invalid invalid -> badRequest(invalid.messages());
            case Purchase purchase -> {
                purchaseAPI.purchase(purchase);
                yield Response.noContent().build();
            }
        };
    }

    private static Response badRequest(List<String> messages) {
        return Response.status(Response.Status.BAD_REQUEST).entity(messages).build();
    }
}
