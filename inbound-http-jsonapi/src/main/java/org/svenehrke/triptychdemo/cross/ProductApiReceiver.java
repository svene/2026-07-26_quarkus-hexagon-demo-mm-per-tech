package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductsAPI;
import org.svenehrke.triptychdemo.cross.purchase.ParsedPurchase;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseAPI;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;
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

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.quarkus.resteasy.reactive.jackson.CustomDeserialization;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import static org.svenehrke.triptychdemo.cross.JsonResponses.badRequest;

@Path("/api/products")
@CustomDeserialization(StrictJsonReader.class)
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
    public Response orderFruits(FruitOrderRequest request) {
        var structureErrors = FruitOrderRequest.structureErrors(request);
        if (!structureErrors.isEmpty()) return badRequest(structureErrors);
        return switch (FruitOrder.parse(request.productName(), request.quantity())) {
            case ParsedFruitOrder.Invalid invalid -> badRequest(messagesOf(invalid.violations()));
            case FruitOrder order -> {
                fruitsAPI.order(order);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-vegetables")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderVegetables(VegetableOrderRequest request) {
        var structureErrors = VegetableOrderRequest.structureErrors(request);
        if (!structureErrors.isEmpty()) return badRequest(structureErrors);
        return switch (VegetableOrder.parse(request.productName(), request.quantity())) {
            case ParsedVegetableOrder.Invalid invalid -> badRequest(messagesOf(invalid.violations()));
            case VegetableOrder order -> {
                vegetablesAPI.order(order);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-dairy")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderDairy(DairyOrderRequest request) {
        var structureErrors = DairyOrderRequest.structureErrors(request);
        if (!structureErrors.isEmpty()) return badRequest(structureErrors);
        return switch (DairyOrder.parse(request.productName(), request.quantity())) {
            case ParsedDairyOrder.Invalid invalid -> badRequest(messagesOf(invalid.violations()));
            case DairyOrder order -> {
                dairyAPI.order(order);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-beverages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderBeverages(BeverageOrderRequest request) {
        var structureErrors = BeverageOrderRequest.structureErrors(request);
        if (!structureErrors.isEmpty()) return badRequest(structureErrors);
        return switch (BeverageOrder.parse(request.productName(), request.quantity())) {
            case ParsedBeverageOrder.Invalid invalid -> badRequest(messagesOf(invalid.violations()));
            case BeverageOrder order -> {
                beveragesAPI.order(order);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-meat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderMeat(MeatOrderRequest request) {
        var structureErrors = MeatOrderRequest.structureErrors(request);
        if (!structureErrors.isEmpty()) return badRequest(structureErrors);
        return switch (MeatOrder.parse(request.productName(), request.quantity())) {
            case ParsedMeatOrder.Invalid invalid -> badRequest(messagesOf(invalid.violations()));
            case MeatOrder order -> {
                meatAPI.order(order);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-bakery")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderBakery(BakeryOrderRequest request) {
        var structureErrors = BakeryOrderRequest.structureErrors(request);
        if (!structureErrors.isEmpty()) return badRequest(structureErrors);
        return switch (BakeryOrder.parse(request.productName(), request.quantity())) {
            case ParsedBakeryOrder.Invalid invalid -> badRequest(messagesOf(invalid.violations()));
            case BakeryOrder order -> {
                bakeryAPI.order(order);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/order-nonfood")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orderNonFood(NonFoodOrderRequest request) {
        var structureErrors = NonFoodOrderRequest.structureErrors(request);
        if (!structureErrors.isEmpty()) return badRequest(structureErrors);
        return switch (NonFoodOrder.parse(request.productName(), request.quantity())) {
            case ParsedNonFoodOrder.Invalid invalid -> badRequest(messagesOf(invalid.violations()));
            case NonFoodOrder order -> {
                nonFoodAPI.order(order);
                yield Response.noContent().build();
            }
        };
    }

    @POST
    @Path("/purchase")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response purchase(PurchaseRequest request) {
        var structureErrors = PurchaseRequest.structureErrors(request);
        if (!structureErrors.isEmpty()) return badRequest(structureErrors);
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

    // --- Deserialization errors (the body never reached this class's methods; not validation).
    // Declared here, not globally, so they only apply to this resource.

    @ServerExceptionMapper
    public Response mapMismatchedInput(MismatchedInputException e) {
        return badRequest(List.of(JsonInputErrors.messageFor(e)));
    }

    @ServerExceptionMapper
    public Response mapWebApplicationException(WebApplicationException e) {
        return JsonInputErrors.messageFor(e).map(message -> badRequest(List.of(message))).orElseGet(e::getResponse);
    }

    private static List<String> messagesOf(Collection<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).toList();
    }
}
