package org.svenehrke.triptychdemo.external.outbound.soap.meat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.svenehrke.triptychdemo.external.outbound.soap.LeadTime;
import jakarta.jws.WebService;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
@WebService(
    endpointInterface = "org.svenehrke.triptychdemo.external.outbound.soap.meat.MeatOrderService",
    serviceName = "MeatOrderService",
    targetNamespace = "http://meatsupplier.example.com/"
)
public class MeatSupplierStub implements MeatOrderService {

    @Inject
    @Channel("meat-deliveries-out")
    Emitter<DeliveryMessage> emitter;

    @Inject
    LeadTime leadTime;

    @Override
    public void placeOrder(String productName, int quantity) {
        leadTime.deliverLater(() -> emitter.send(new DeliveryMessage(productName, quantity)));
    }
}
