package com.example.hexademo.adapter.inbound.rest;

import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.PurchaseItem;
import com.example.hexademo.core.port.in.ProductsAPI;
import com.example.hexademo.core.port.in.PurchaseAPI;
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
import java.util.ArrayList;
import java.util.List;

@Path("/shop")
public class ShopReceiver {

    @Inject ProductsAPI productsAPI;
    @Inject PurchaseAPI purchaseAPI;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance shop(List<Product> products);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        var inStock = productsAPI.listAll().stream()
            .filter(p -> p.availableAmount() > 0)
            .toList();
        return Templates.shop(inStock);
    }

    @POST
    @Path("/checkout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response checkout(@FormParam("productName") List<String> productNames,
                             @FormParam("quantity") List<String> quantities) {
        var items = new ArrayList<PurchaseItem>();
        if (productNames != null) {
            for (int i = 0; i < productNames.size(); i++) {
                var name = productNames.get(i);
                var qtyStr = quantities != null && i < quantities.size() ? quantities.get(i) : null;
                if (name == null || name.isBlank() || qtyStr == null || qtyStr.isBlank()) continue;
                int qty;
                try {
                    qty = Integer.parseInt(qtyStr.trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (qty > 0) items.add(new PurchaseItem(name, qty));
            }
        }
        if (!items.isEmpty()) purchaseAPI.purchase(items);
        return Response.seeOther(URI.create("/shop")).build();
    }
}
