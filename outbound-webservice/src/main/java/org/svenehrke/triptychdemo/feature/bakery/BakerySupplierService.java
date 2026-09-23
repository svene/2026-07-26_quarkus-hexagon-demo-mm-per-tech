package org.svenehrke.triptychdemo.feature.bakery;

import io.quarkiverse.cxf.annotation.CXFClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BakerySupplierService implements BakerySupplierSPI {

    @Inject
    @CXFClient("bakery-supplier")
    BakeryOrderService client;

    @Override
    public void placeOrder(BakeryOrder bakeryOrder) {
        client.placeOrder(bakeryOrder.productName(), bakeryOrder.quantity());
    }
}
