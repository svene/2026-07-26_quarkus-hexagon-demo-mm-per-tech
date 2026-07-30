package com.example.hexademo.core.port.out;

import com.example.hexademo.core.domain.Product;

public interface InventoryEventPublisherSPI {
    void publishInventoryChanged(Product product);
}
