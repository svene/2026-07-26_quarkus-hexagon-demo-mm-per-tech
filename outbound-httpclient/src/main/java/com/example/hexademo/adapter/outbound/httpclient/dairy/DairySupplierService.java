package com.example.hexademo.adapter.outbound.httpclient.dairy;

import com.example.hexademo.core.application.SPIs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class DairySupplierService implements SPIs.DairySupplierSPI {

    @Inject
    @RestClient
    DairySupplierClient client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(new OrderRequest(productName, quantity));
    }
}
