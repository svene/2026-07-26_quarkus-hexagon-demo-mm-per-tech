package org.svenehrke.triptychdemo.feature.meat;

import io.quarkiverse.cxf.annotation.CXFClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MeatSupplierService implements MeatSupplierSPI {

    @Inject
    @CXFClient("meat-supplier")
    MeatOrderService client;

    @Override
    public void placeOrder(MeatOrder meatOrder) {
        client.placeOrder(meatOrder.productName(), meatOrder.quantity());
    }
}
