package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import io.smallrye.config.ConfigMapping;

import java.time.Duration;
import java.util.Map;

/** {@code cashpoint-stub.*} in app-server's application.properties. */
@ConfigMapping(prefix = "cashpoint-stub")
public interface CashpointStubConfig {

    /** How often the simulation advances; {@code off} stops the stub (tests, e2e). Read by {@code @Scheduled}. */
    String tick();

    /** See {@link SimulationTiming}. */
    Duration day();

    /** See {@link SimulationTiming}. */
    Duration tillTime();

    /** Per store id: the external checkout system's store. */
    Map<String, Store> stores();

    interface Store {
        int tills();
    }
}
