package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.events.AsyncEvents;
import org.svenehrke.triptychdemo.cross.inventory.SupplierOrdersChanged;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Core's async events reach their observers on a virtual thread (the {@code @EventExecutor} inbound-event produces),
 * not on CDI's default executor, Quarkus' platform worker pool.
 */
@QuarkusTest
class AsyncEventsTest {

    @Inject AsyncEvents asyncEvents;
    @Inject TestEventThreadRecorder recorder;

    @Test
    void observers_run_on_a_virtual_thread() {
        recorder.threads.clear();

        asyncEvents.fire(new SupplierOrdersChanged());

        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(recorder.threads).isNotEmpty());
        assertThat(recorder.threads).allSatisfy(thread -> assertThat(thread.isVirtual()).isTrue());
    }
}
