package org.svenehrke.triptychdemo.cross.products;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ProductsHandler implements ProductsAPI {

    @Inject
    InventoryRepositorySPI inventoryRepository;

    @Override
    public List<Product> listAll() {
        return inventoryRepository.findAll();
    }
}
