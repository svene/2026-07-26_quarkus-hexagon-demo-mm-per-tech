package org.svenehrke.triptychdemo.external.outbound.kafka.carrier;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.svenehrke.triptychdemo.external.outbound.kafka.LeadTime;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.time.Duration;

/** The carrier between the DC and the locations: reports a shipment as arrived {@code carrier-stub.transit-time} ± 20% after it was dispatched. */
@ApplicationScoped
public class CarrierStub {

    @Inject
    @Channel("shipment-arrivals-out")
    Emitter<ShipmentArrivalMessage> emitter;

    @Inject
    LeadTime leadTime;

    @ConfigProperty(name = "carrier-stub.transit-time", defaultValue = "0s")
    Duration transitTime;

    @Incoming("shipments")
    @Blocking
    public void processShipment(ShipmentMessage shipment) {
        leadTime.later(transitTime, () -> emitter.send(new ShipmentArrivalMessage(shipment.shipmentId())));
    }
}
