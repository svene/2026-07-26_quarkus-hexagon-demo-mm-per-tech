package com.example.hexademo.adapter.outbound.httpclient.vegetable;

import com.example.hexademo.core.application.SPIs;
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
