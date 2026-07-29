package com.example.hexdemo.adapter.outbound.httpclient;

import com.example.hexdemo.core.port.out.FruitSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class FruitSupplierService implements FruitSupplierSPI {

    @Inject
    @RestClient
    FruitSupplierClient client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(new FruitOrderRequest(productName, quantity));
    }
}
