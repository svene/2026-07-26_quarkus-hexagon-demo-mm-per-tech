package com.example.hexdemo.adapter.outbound.webservice;

import com.example.hexdemo.core.port.out.BeverageSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class BeverageSupplierService implements BeverageSupplierSPI {

    @Inject
    @RestClient
    BeverageSupplierClient client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(new BeverageOrderRequest(productName, quantity));
    }
}
