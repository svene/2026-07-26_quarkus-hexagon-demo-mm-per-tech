package com.example.hexarcdemo.core.port.out;

public interface VegetablesSupplierSPI {
    void placeOrder(String productName, int quantity);
}
