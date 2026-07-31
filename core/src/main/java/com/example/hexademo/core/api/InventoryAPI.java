package com.example.hexademo.core.api;

public interface InventoryAPI {
    void updateFruitAmount(String productName, int quantity);
    void updateVegetableAmount(String productName, int quantity);
    void updateDairyAmount(String productName, int quantity);
    void updateBeverageAmount(String productName, int quantity);
    void updateMeatAmount(String productName, int quantity);
    void updateBakeryAmount(String productName, int quantity);
    void updateNonFoodAmount(String productName, int quantity);
}
