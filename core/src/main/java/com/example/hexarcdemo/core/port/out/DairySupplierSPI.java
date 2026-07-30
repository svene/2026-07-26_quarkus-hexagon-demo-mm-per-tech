package com.example.hexarcdemo.core.port.out;

public interface DairySupplierSPI {
    void placeOrder(String productName, int quantity);
}
