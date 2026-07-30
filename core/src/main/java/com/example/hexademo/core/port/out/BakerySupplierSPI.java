package com.example.hexademo.core.port.out;

public interface BakerySupplierSPI {
    void placeOrder(String productName, int quantity);
}
