package com.example.hexdemo.adapter.external.beveragesupplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jws.WebService;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
@WebService(
    endpointInterface = "com.example.hexdemo.adapter.external.beveragesupplier.BeverageOrderService",
    serviceName = "BeverageOrderService",
    targetNamespace = "http://beveragesupplier.example.com/"
)
public class BeverageSupplierStub implements BeverageOrderService {

    @Inject
    @Channel("beverages-deliveries-out")
    Emitter<DeliveryMessage> emitter;

    @Override
    public void placeOrder(String productName, int quantity) {
        emitter.send(new DeliveryMessage(productName, quantity));
    }
}
