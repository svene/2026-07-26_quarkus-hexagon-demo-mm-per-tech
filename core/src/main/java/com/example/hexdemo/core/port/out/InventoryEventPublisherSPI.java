package com.example.hexdemo.core.port.out;

import com.example.hexdemo.core.domain.Product;

public interface InventoryEventPublisherSPI {
    void publishInventoryChanged(Product product);
}
