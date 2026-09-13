package org.svenehrke.triptychdemo.adapter.outbound.httpclient.vegetable;

import org.svenehrke.triptychdemo.core.application.SPIs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class VegetablesSupplierService implements SPIs.VegetablesSupplierSPI {

    @Inject
    @RestClient
    VegetablesSupplierClient client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(new OrderRequest(productName, quantity));
    }
}
