package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** Stock per location, e.g. for the store cashpoints. */
@Path("/api/locations")
public class LocationApiReceiver {

    @Inject
    ProductsHandler productsHandler;

    @GET
    @Path("/{id}/products")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> list(@PathParam("id") String id) {
        var location = Locations.byId(id).orElseThrow(() -> new NotFoundException("unknown location: " + id));
        return productsHandler.listAll(location);
    }
}
