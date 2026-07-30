package com.example.hexarcdemo.adapter.outbound.webservice;

import com.example.hexarcdemo.core.port.out.BakerySupplierSPI;
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
