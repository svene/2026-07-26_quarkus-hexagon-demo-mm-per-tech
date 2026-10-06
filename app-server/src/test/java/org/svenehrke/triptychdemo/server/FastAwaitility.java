package org.svenehrke.triptychdemo.server;

import io.quarkus.test.junit.callback.QuarkusTestBeforeClassCallback;
import org.awaitility.Awaitility;

import java.time.Duration;

/**
 * Awaitility checks right away and then every 50 ms, instead of its defaults (first check after 100 ms, then every
 * 100 ms), which made every {@code await()} - one per test in most {@code setUp()}s - cost at least 100 ms. Set per
 * {@code @QuarkusTest} class, because Quarkus loads the tests (and Awaitility) in its own classloader. Registered in
 * {@code META-INF/services}.
 */
public class FastAwaitility implements QuarkusTestBeforeClassCallback {

    @Override
    public void beforeClass(Class<?> testClass) {
        Awaitility.setDefaultPollDelay(Duration.ZERO);
        Awaitility.setDefaultPollInterval(Duration.ofMillis(50));
    }
}
