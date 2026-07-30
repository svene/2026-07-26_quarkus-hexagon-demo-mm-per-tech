package com.example.hexademo.core.port.out;

public interface MeatSupplierSPI {
    void placeOrder(String productName, int quantity);
}
