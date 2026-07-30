package com.example.hexarcdemo.core.application;

import com.example.hexarcdemo.core.domain.Product;
import com.example.hexarcdemo.core.port.in.ProductsAPI;
import com.example.hexarcdemo.core.port.out.InventoryRepositorySPI;
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
