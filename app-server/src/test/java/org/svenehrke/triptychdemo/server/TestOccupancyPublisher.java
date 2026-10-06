package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.occupancy.OccupancyMessage;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TestOccupancyPublisher {

    @Inject @Channel("testing-store-occupancy-out") Emitter<OccupancyMessage> emitter;

    public void publish(OccupancyMessage message) {
        emitter.send(message);
    }
}
