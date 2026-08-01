package com.example.hexademo.adapter.outbound.webservice.beverage;

import com.example.hexademo.core.application.SPIs;
import io.quarkiverse.cxf.annotation.CXFClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeverageSupplierService implements SPIs.BeverageSupplierSPI {

    @Inject
    @CXFClient("beverage-supplier")
    BeverageOrderService client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(productName, quantity);
    }
}
