package org.svenehrke.triptychdemo.feature.vegetable;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class VegetablesSupplierService implements VegetablesSupplierSPI {

    @Inject
    @RestClient
    VegetablesSupplierClient client;

    @Override
    public void placeOrder(VegetableOrder vegetableOrder) {
        client.placeOrder(new OrderRequest(vegetableOrder.productName(), vegetableOrder.quantity()));
    }
}
