package com.example.hexarcdemo.adapter.outbound.webservice;

import com.example.hexarcdemo.core.port.out.MeatSupplierSPI;
import io.quarkiverse.cxf.annotation.CXFClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MeatSupplierService implements MeatSupplierSPI {

    @Inject
    @CXFClient("meat-supplier")
    MeatOrderService client;

    @Override
    public void placeOrder(String productName, int quantity) {
        client.placeOrder(productName, quantity);
    }
}
