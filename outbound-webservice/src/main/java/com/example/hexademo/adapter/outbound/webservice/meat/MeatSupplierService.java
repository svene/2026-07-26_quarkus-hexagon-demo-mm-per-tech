package com.example.hexademo.adapter.outbound.webservice.meat;

import com.example.hexademo.core.port.out.MeatSupplierSPI;
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
