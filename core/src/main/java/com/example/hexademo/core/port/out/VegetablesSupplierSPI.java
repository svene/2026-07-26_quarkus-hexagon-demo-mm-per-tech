package com.example.hexademo.core.port.out;

public interface VegetablesSupplierSPI {
    void placeOrder(String productName, int quantity);
}
