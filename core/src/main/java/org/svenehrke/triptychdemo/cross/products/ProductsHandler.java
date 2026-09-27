package org.svenehrke.triptychdemo.cross.products;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ProductsHandler {

    @Inject
    InventoryRepositorySPI inventoryRepository;

    public List<Product> listAll() {
        return inventoryRepository.findAll();
    }
}
