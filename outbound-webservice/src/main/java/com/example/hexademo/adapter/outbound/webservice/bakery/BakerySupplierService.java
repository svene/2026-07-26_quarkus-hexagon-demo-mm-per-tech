package com.example.hexademo.adapter.outbound.webservice.bakery;

import com.example.hexademo.core.spi.BakerySupplierSPI;
import io.quarkiverse.cxf.annotation.CXFClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BakerySupplierService implements BakerySupplierSPI {

    @Inject
    @CXFClient("bakery-supplier")
    BakeryOrderService client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(productName, quantity);
    }
}
