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
import java.util.function.Supplier;

/**
 * The carrier between the DC and the locations: reports a shipment as arrived {@code carrier-stub.transit-time} ± 20%
 * after it was dispatched. Looked up per shipment, so a test can switch it without its own Quarkus instance.
 */
@ApplicationScoped
public class CarrierStub {

    @Inject
    @Channel("shipment-arrivals-out")
    Emitter<ShipmentArrivalMessage> emitter;

    @Inject
    LeadTime leadTime;

    @ConfigProperty(name = "carrier-stub.transit-time", defaultValue = "0s")
    Supplier<Duration> transitTime;

    @Incoming("shipments")
    @Blocking
    public void processShipment(ShipmentMessage shipment) {
        leadTime.later(transitTime.get(), () -> emitter.send(new ShipmentArrivalMessage(shipment.shipmentId())));
    }
}
