package com.example.hexademo.core.port.out;

import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.ProductType;
import java.util.List;

public interface InventoryRepositorySPI {
    Product addAmount(String name, ProductType type, int delta);
    Product deductAmount(String name, int delta);
    List<Product> findAll();
}
