package org.svenehrke.triptychdemo.feature.dairy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class DairySupplierService implements DairySupplierSPI {

    @Inject
    @RestClient
    DairySupplierClient client;

    @Override
    public void placeOrder(DairyOrder dairyOrder) {
        client.placeOrder(new OrderRequest(dairyOrder.productName(), dairyOrder.quantity()));
    }
}
