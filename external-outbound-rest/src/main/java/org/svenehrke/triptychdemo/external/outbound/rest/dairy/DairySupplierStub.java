package org.svenehrke.triptychdemo.external.outbound.rest.dairy;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.svenehrke.triptychdemo.external.outbound.rest.LeadTime;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
@RunOnVirtualThread
@Path("/dairy-orders")
public class DairySupplierStub {

    @Inject
    @Channel("dairy-deliveries-out")
    Emitter<DeliveryMessage> emitter;

    @Inject
    LeadTime leadTime;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void placeOrder(OrderRequest request) {
        leadTime.deliverLater(() -> emitter.send(new DeliveryMessage(request.productName(), request.quantity())));
    }
}
