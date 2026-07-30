package com.example.hexdemo.core.port.in;

public interface InventoryAPI {
    void updateFruitAmount(String productName, int quantity);
    void updateBeverageAmount(String productName, int quantity);
}
