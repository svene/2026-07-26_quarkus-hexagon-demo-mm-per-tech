package com.example.hexarcdemo.adapter.external.beveragesupplier;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;

@WebService(name = "MeatOrderService", targetNamespace = "http://meatsupplier.example.com/")
public interface MeatOrderService {
    @WebMethod
    void placeOrder(
        @WebParam(name = "productName") String productName,
        @WebParam(name = "quantity") int quantity
    );
}
