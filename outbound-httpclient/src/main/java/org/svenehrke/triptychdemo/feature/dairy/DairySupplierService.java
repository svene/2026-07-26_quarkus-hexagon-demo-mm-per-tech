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
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(new OrderRequest(productName, quantity));
    }
}
