package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.location.Store;
import org.svenehrke.triptychdemo.cross.occupancy.OccupancyHandler;
import org.svenehrke.triptychdemo.cross.occupancy.ParsedTillCount;
import org.svenehrke.triptychdemo.cross.occupancy.StoreOccupancy;
import org.svenehrke.triptychdemo.cross.occupancy.TillCount;
import org.svenehrke.triptychdemo.cross.products.ProductStock;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;
import org.svenehrke.triptychdemo.cross.reorder.DemandEstimate;
import org.svenehrke.triptychdemo.cross.replenishment.ParsedStockRequest;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentHandler;
import org.svenehrke.triptychdemo.cross.replenishment.StockRequest;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * One page with every store and the online FC (the DC is managed on {@code /admin}): per location its stock, and
 * requesting more from the DC; per store its occupancy, and opening/closing its tills.
 */
@RunOnVirtualThread
@Path("/locations")
public class LocationReceiver {

    private static final int REQUEST_LIMIT = 20;

    @Inject
    ProductsHandler productsHandler;
    @Inject
    ReplenishmentHandler replenishmentHandler;
    @Inject
    OccupancyHandler occupancyHandler;
    @Inject
    AuditLogHandler auditLog;

    /** The static page shell; its {@code #app} element loads {@link #page()} and renders it in the browser. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String shell() {
        return PageShell.render("/shells/locations.html", Map.of());
    }

    @GET
    @Path("/page")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse page() {
        var products = productsHandler.listAllLocations();
        var occupancy = occupancyHandler.current();
        return UiResponse.of(UiRoute.LocationsPage,
            new LocationsPageVM(Locations.REPLENISHED.stream().map(l -> inventory(l, products)).toList(),
                Locations.REPLENISHED.stream().filter(Store.class::isInstance).map(Store.class::cast)
                    .map(s -> occupancy(s, occupancy)).toList()));
    }

    @GET
    @Path("/{id}/occupancy-fragment")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse occupancyFragment(@PathParam("id") String id) {
        return UiResponse.of(UiRoute.StoreOccupancy, occupancy(store(id), occupancyHandler.current()));
    }

    /** Opens or closes tills: {@code tills} is the new number (the page sends the reported one ± 1). */
    @POST
    @Path("/{id}/tills")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response tills(@PathParam("id") String id, @FormParam("tills") int tills) {
        var store = store(id);
        auditLog.log("LocationReceiver: TILLS_RECEIVED", store.id() + ": tills=" + tills);
        return switch (TillCount.parse(store, tills)) {
            case ParsedTillCount.Invalid invalid -> errors(Response.Status.BAD_REQUEST,
                invalid.violations().stream().map(v -> "tills " + v.getMessage()).toList());
            // the new tills show up with the checkout system's next report, right after the change
            case TillCount tillCount -> occupancyHandler.setTills(tillCount)
                ? Response.ok("", MediaType.TEXT_HTML).build()
                : errors(Response.Status.BAD_GATEWAY, List.of(store.name() + ": the checkout system did not accept the change"));
        };
    }

    @GET
    @Path("/{id}/inventory-fragment")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse inventoryFragment(@PathParam("id") String id) {
        return UiResponse.of(UiRoute.LocationInventory, inventory(location(id), productsHandler.listAllLocations()));
    }

    @POST
    @Path("/{id}/requests")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response request(@PathParam("id") String id,
                            @FormParam("productName") String productName,
                            @FormParam("quantity") String quantity) {
        var location = location(id);
        auditLog.log("LocationReceiver: REQUEST_RECEIVED", location.id() + ": " + productName + " qty=" + quantity);
        return switch (StockRequest.parse(location, productName, quantity)) {
            case ParsedStockRequest.Invalid invalid -> errors(Response.Status.BAD_REQUEST,
                invalid.violations().stream().map(ConstraintViolation::getMessage).toList());
            case StockRequest request -> replenishmentHandler.request(request).isPresent()
                // 200 with an empty body (not 204, which htmx never swaps) clears a previous error below the form
                ? Response.ok("", MediaType.TEXT_HTML).build()
                : errors(Response.Status.CONFLICT, List.of(productName + ": not carried by the DC"));
        };
    }

    private static Replenished location(String id) {
        return Locations.replenishedById(id)
            .orElseThrow(() -> new NotFoundException("no store or online FC: " + id));
    }

    private static Store store(String id) {
        return Locations.storeById(id).orElseThrow(() -> new NotFoundException("no store: " + id));
    }

    private static StoreOccupancyVM occupancy(Store store, List<StoreOccupancy> occupancy) {
        return StoreOccupancyVM.of(store, occupancy.stream().filter(o -> o.store().equals(store)).findFirst(), Instant.now());
    }

    private static Response errors(Response.Status status, List<String> messages) {
        return UiResponse.response(status, UiRoute.OrderErrors, new OrderErrorsVM(messages));
    }

    /** Every product the DC carries, so one this location has none of can be requested, too. */
    private LocationInventoryVM inventory(Replenished location, List<ProductStock> allProducts) {
        var products = allProducts.stream()
            .map(p -> new LocationProductRowVM(p.name(), p.type().name(), p.availableAt(location), p.inTransitTo(location), p.availableAt(Locations.DC),
                p.estimateAt(location).map(DemandEstimate::avg).orElse(null), p.levelsAt(location).map(LevelsVM::of).orElse(null)))
            .sorted(Comparator.comparing(LocationProductRowVM::name, String.CASE_INSENSITIVE_ORDER).thenComparing(LocationProductRowVM::type))
            .toList();
        var requests = replenishmentHandler.listRecent(location, REQUEST_LIMIT).stream().map(RequestVM::of).toList();
        return new LocationInventoryVM(location.id(), location.name(), products, requests);
    }
}
