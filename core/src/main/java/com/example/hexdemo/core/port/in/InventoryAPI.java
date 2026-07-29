package com.example.hexdemo.core.port.in;

import com.example.hexdemo.core.domain.ProductType;

public interface InventoryAPI {
    void updateAmount(String productName, ProductType type, int additionalAmount);
}
