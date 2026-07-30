package com.example.hexademo.core.port.out;

public interface BeverageSupplierSPI {
    void placeOrder(String productName, int quantity);
}
