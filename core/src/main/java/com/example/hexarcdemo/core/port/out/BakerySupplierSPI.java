package com.example.hexarcdemo.core.port.out;

public interface BakerySupplierSPI {
    void placeOrder(String productName, int quantity);
}
