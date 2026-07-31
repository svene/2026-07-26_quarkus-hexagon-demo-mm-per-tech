package com.example.hexademo.adapter.outbound.webservice.beverage;

import com.example.hexademo.core.spi.BeverageSupplierSPI;
import io.quarkiverse.cxf.annotation.CXFClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeverageSupplierService implements BeverageSupplierSPI {

    @Inject
    @CXFClient("beverage-supplier")
    BeverageOrderService client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(productName, quantity);
    }
}
