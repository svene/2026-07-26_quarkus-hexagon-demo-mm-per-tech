package com.example.hexarcdemo.core.port.out;

public interface BeverageSupplierSPI {
    void placeOrder(String productName, int quantity);
}
