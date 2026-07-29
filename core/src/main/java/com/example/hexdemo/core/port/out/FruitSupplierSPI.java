package com.example.hexdemo.core.port.out;

public interface FruitSupplierSPI {
    void placeOrder(String productName, int quantity);
}
