package com.example.hexarcdemo.core.port.out;

import com.example.hexarcdemo.core.domain.Product;
import com.example.hexarcdemo.core.domain.ProductType;
import java.util.List;

public interface InventoryRepositorySPI {
    Product addAmount(String name, ProductType type, int delta);
    Product deductAmount(String name, int delta);
    List<Product> findAll();
}
