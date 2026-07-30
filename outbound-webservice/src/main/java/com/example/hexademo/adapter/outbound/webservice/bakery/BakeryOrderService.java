package com.example.hexademo.adapter.outbound.webservice.bakery;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;

@WebService(name = "BakeryOrderService", targetNamespace = "http://bakerysupplier.example.com/")
public interface BakeryOrderService {
    @WebMethod
    void placeOrder(
        @WebParam(name = "productName") String productName,
        @WebParam(name = "quantity") int quantity
    );
}
