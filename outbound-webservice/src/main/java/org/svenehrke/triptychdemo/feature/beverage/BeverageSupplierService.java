package org.svenehrke.triptychdemo.feature.beverage;

import io.quarkiverse.cxf.annotation.CXFClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeverageSupplierService implements BeverageSupplierSPI {

    @Inject
    @CXFClient("beverage-supplier")
    BeverageOrderService client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(productName, quantity);
    }
}
