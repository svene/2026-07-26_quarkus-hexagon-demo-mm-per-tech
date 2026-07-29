package com.example.hexdemo.core.port.out;

public interface BeverageSupplierSPI {
    void placeOrder(String productName, int quantity);
}
