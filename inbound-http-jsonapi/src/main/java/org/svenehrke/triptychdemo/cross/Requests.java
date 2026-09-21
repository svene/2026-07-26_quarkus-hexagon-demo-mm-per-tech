package org.svenehrke.triptychdemo.cross;

import java.util.List;

/**
 * Request record types for JSON API endpoints.
 * Each inner record corresponds to a product category order request or purchase request.
 * Deliberately unvalidated here: validation constraints live once on the domain type each
 * request is turned into (e.g. {@code FruitOrder}), not duplicated onto these wire-format DTOs.
 */
public interface Requests {

  record FruitOrderRequest(String productName, int quantity) {}

  record VegetableOrderRequest(String productName, int quantity) {}

  record DairyOrderRequest(String productName, int quantity) {}

  record BeverageOrderRequest(String productName, int quantity) {}

  record MeatOrderRequest(String productName, int quantity) {}

  record BakeryOrderRequest(String productName, int quantity) {}

  record NonFoodOrderRequest(String productName, int quantity) {}

  record PurchaseRequest(List<PurchaseRequestItem> items) {}

  record PurchaseRequestItem(String productName, int quantity) {}

}
