package com.example.hexademo.core.port.out;

public interface DairySupplierSPI {
    void placeOrder(String productName, int quantity);
}
