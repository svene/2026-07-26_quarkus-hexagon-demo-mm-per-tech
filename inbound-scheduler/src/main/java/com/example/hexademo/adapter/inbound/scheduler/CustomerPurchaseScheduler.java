package com.example.hexademo.adapter.inbound.scheduler;

import com.example.hexademo.core.port.in.ProductsAPI;
import com.example.hexademo.core.port.in.PurchaseAPI;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class CustomerPurchaseScheduler {

    @Inject ProductsAPI productsAPI;
    @Inject PurchaseAPI purchaseAPI;

    @Scheduled(every = "2s", delayed = "10s")
    void simulatePurchase() {
        var products = productsAPI.listAll();
        if (products.isEmpty()) return;
        var rnd = ThreadLocalRandom.current();
        var product = products.get(rnd.nextInt(products.size()));
        int qty = rnd.nextInt(1, 4);
        purchaseAPI.purchase(product.name(), qty);
    }
}
