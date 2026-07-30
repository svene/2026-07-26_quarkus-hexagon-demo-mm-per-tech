package com.example.hexademo.core.port.out;

public interface NonFoodSupplierSPI {
    void placeOrder(String productName, int quantity);
}
