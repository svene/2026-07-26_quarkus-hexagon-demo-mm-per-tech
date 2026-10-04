package org.svenehrke.triptychdemo.cross.replenishment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/** One topic for all commodities: the carrier is one external system, whatever it carries. */
@ApplicationScoped
public class CarrierService implements CarrierSPI {

    @Inject
    @Channel("shipments-out")
    Emitter<ShipmentMessage> emitter;

    @Override
    public void dispatch(Shipment shipment) {
        emitter.send(new ShipmentMessage(shipment.id(), shipment.location().id(), shipment.productName(), shipment.quantity()));
    }
}
