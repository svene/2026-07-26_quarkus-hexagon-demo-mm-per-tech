package com.example.hexademo.core.spi;

public interface DairySupplierSPI {
    void placeOrder(String productName, int quantity);
}
