package com.example.hexarcdemo.adapter.external.beveragesupplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jws.WebService;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
@WebService(
    endpointInterface = "com.example.hexarcdemo.adapter.external.beveragesupplier.BakeryOrderService",
    serviceName = "BakeryOrderService",
    targetNamespace = "http://bakerysupplier.example.com/"
)
public class BakerySupplierStub implements BakeryOrderService {

    @Inject
    @Channel("bakery-deliveries-out")
    Emitter<DeliveryMessage> emitter;

    @Override
    public void placeOrder(String productName, int quantity) {
        emitter.send(new DeliveryMessage(productName, quantity));
    }
}
