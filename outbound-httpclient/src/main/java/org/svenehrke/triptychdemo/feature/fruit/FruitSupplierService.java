package org.svenehrke.triptychdemo.feature.fruit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class FruitSupplierService implements FruitSupplierSPI {

    @Inject
    @RestClient
    FruitSupplierClient client;

    @Override
    public void placeOrder(FruitOrder fruitOrder) {
        client.placeOrder(new OrderRequest(fruitOrder.productName(), fruitOrder.quantity()));
    }
}
