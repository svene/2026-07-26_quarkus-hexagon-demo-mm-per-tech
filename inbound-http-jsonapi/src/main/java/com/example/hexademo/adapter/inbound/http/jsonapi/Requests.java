package com.example.hexademo.adapter.inbound.http.jsonapi;

import java.util.List;

/**
 * Request record types for JSON API endpoints.
 * Each inner record corresponds to a product category order request or purchase request.
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
