package com.example.hexarcdemo.core.port.out;

import com.example.hexarcdemo.core.domain.Product;

public interface InventoryEventPublisherSPI {
    void publishInventoryChanged(Product product);
}
