package com.example.hexarcdemo.external.soap.meat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jws.WebService;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
@WebService(
    endpointInterface = "com.example.hexarcdemo.external.soap.meat.MeatOrderService",
    serviceName = "MeatOrderService",
    targetNamespace = "http://meatsupplier.example.com/"
)
public class MeatSupplierStub implements MeatOrderService {

    @Inject
    @Channel("meat-deliveries-out")
    Emitter<DeliveryMessage> emitter;

    @Override
    public void placeOrder(String productName, int quantity) {
        emitter.send(new DeliveryMessage(productName, quantity));
    }
}
