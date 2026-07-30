package com.example.hexarcdemo.adapter.outbound.httpclient;

import com.example.hexarcdemo.core.port.out.DairySupplierSPI;
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
        client.placeOrder(new FruitOrderRequest(productName, quantity));
    }
}
