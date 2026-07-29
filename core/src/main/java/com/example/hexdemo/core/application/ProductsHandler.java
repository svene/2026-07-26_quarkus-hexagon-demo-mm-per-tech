package com.example.hexdemo.core.application;

import com.example.hexdemo.core.domain.Product;
import com.example.hexdemo.core.port.in.ProductsAPI;
import com.example.hexdemo.core.port.out.InventoryRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ProductsHandler implements ProductsAPI {

    @Inject InventoryRepositorySPI inventoryRepository;

    @Override
    public List<Product> listAll() {
        return inventoryRepository.findAll();
    }
}
