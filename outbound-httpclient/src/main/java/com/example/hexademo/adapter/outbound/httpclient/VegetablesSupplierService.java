package com.example.hexademo.adapter.outbound.httpclient;

import com.example.hexademo.core.port.out.VegetablesSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class VegetablesSupplierService implements VegetablesSupplierSPI {

    @Inject
    @RestClient
    VegetablesSupplierClient client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(new FruitOrderRequest(productName, quantity));
    }
}
