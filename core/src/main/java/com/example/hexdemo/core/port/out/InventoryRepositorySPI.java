package com.example.hexdemo.core.port.out;

import com.example.hexdemo.core.domain.Product;
import com.example.hexdemo.core.domain.ProductType;
import java.util.List;

public interface InventoryRepositorySPI {
    Product addAmount(String name, ProductType type, int delta);
    List<Product> findAll();
}
