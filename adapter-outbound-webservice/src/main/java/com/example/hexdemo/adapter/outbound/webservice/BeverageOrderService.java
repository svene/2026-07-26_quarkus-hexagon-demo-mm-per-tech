package com.example.hexdemo.adapter.outbound.webservice;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;

@WebService(name = "BeverageOrderService", targetNamespace = "http://beveragesupplier.example.com/")
public interface BeverageOrderService {
    @WebMethod
    void placeOrder(
        @WebParam(name = "productName") String productName,
        @WebParam(name = "quantity") int quantity
    );
}
