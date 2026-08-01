package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.Product;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ProductsHandler implements APIs.ProductsAPI {

    @Inject
    SPIs.InventoryRepositorySPI inventoryRepository;

    @Override
    public List<Product> listAll() {
        return inventoryRepository.findAll();
    }
}
