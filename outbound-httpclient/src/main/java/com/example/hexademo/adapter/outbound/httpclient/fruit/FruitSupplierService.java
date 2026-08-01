package com.example.hexademo.adapter.outbound.httpclient.fruit;

import com.example.hexademo.core.application.SPIs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class FruitSupplierService implements SPIs.FruitSupplierSPI {

    @Inject
    @RestClient
    FruitSupplierClient client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(new OrderRequest(productName, quantity));
    }
}
