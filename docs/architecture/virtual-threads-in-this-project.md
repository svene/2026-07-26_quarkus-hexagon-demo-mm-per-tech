# Virtual Threads in This Project

How this demo applies the modern Quarkus programming model: blocking code on virtual threads, Mutiny only for
streams and framework interfaces. Background is in [`virtual-threads-vs-mutiny.md`](virtual-threads-vs-mutiny.md),
general migration advice in [`virtual-threads-migrating-from-mutiny.md`](virtual-threads-migrating-from-mutiny.md).
The work is tracked in `PLAN.md` (`virtual-threads`).

State as of 2026-10-06: converted. Java 25 (`maven.compiler.release` 25, `.sdkmanrc`), Quarkus 3.33.

---

## Starting point

The demo never went reactive. Handlers, SPIs and Services return plain types. Persistence is plain JDBC
(`outbound-postgres`) and classic MongoDB Panache, and supplier calls use the REST client and CXF. So its
**programming model already was the modern one**. What was missing was the execution side: the blocking code ran on
**platform worker threads**, so every wait held one.

Moving to virtual threads therefore **didn't make the code simpler** (there were no `Uni` chains to remove), but it
**stopped blocking platform threads** without changing the code. All libraries stayed as they were.

---

## What runs where

| Part | Runs on | How |
|---|---|---|
| REST receivers (`inbound-http-html`, `inbound-http-jsonapi`; stubs `Fruit/Vegetables/DairySupplierStub`, `CashpointTillsStub`) | virtual thread | `@RunOnVirtualThread` on the class |
| Kafka consumers (10 receivers in `inbound-kafka`, `CarrierStub`, `NonFoodSupplierStub`) | virtual thread, one message at a time per channel | `@RunOnVirtualThread` instead of `@Blocking`, plus `smallrye.messaging.worker.<virtual-thread>.max-concurrency=1` |
| `@Scheduled` (`DemandPeriodReceiver`, `CashpointStub`) | virtual thread | `@RunOnVirtualThread` on the method |
| Async event observers (`inbound-event`: delivery, auto-replenishment, auto-purchasing, shipment catch-up, DC seed; the SSE broadcaster) | virtual thread, one per event | core fires through `AsyncEvents` on the `@EventExecutor`, which `inbound-event` produces as Quarkus' `@VirtualThreads` executor |
| DC seed at startup (`DcSeedReceiver.onStart`) | virtual thread | the one-off Vert.x timer hands the seed to the `@VirtualThreads` executor |
| SSE stream (`InventoryEventsReceiver`) | event loop | stays `Multi`: a stream that holds no thread while idle |
| `DeadLetterOrFailStop` | event loop | stays: `Uni` is SmallRye's `KafkaFailureHandler` interface |
| `LeadTime` (delayed stub deliveries) | Quarkus' worker pool, timer | stays: holds no thread while waiting |
| SOAP stubs (`external-outbound-soap`, CXF endpoints) | CXF worker thread | stays: quarkus-cxf 3.33 can't run service endpoints on virtual threads, and the stubs don't block (they only schedule the delivery) |

What's left of Mutiny in our code: the SSE `Multi`, the `Uni` that SmallRye's failure handler interface requires,
and `LeadTime`'s use of Mutiny's `Infrastructure` to reach Quarkus' worker pool.

---

## Decisions

### Kafka: virtual threads, but one message at a time

With `@RunOnVirtualThread`, SmallRye processes a channel's messages **concurrently and out of order** (up to 1024 at
once by default). A spike showed it: 10 messages all started before the first finished, and they finished in random
order. The receivers rely on one-at-a-time processing: `DeadLetterOrFailStop` stops a channel after a persistent
failure, and the messages behind it must not be processed in the meantime. The setting
`smallrye.messaging.worker.<virtual-thread>.max-concurrency=1` (in `application.properties`, applies to each method on
its own) restores that. The messages now run one at a time, in order, each on a virtual thread.

### Async events: on the executor, not handed off per observer

Core fires its events (`InventoryEvent`s, `OccupancyChanged`) with `Event.fireAsync`. CDI delivers those on its
default executor, which in Quarkus is the platform worker pool. ArC notifies the async observers of one event **one
after another in a single task**. If each observer had handed its work to a virtual thread of its own, the observers
of `LevelsRecalculated` (DC seed, auto-purchasing, auto-replenishment, shipment catch-up) would have run in parallel,
for example ordering the same product from a supplier twice.

So the executor is changed where the event is fired, not in the observers:

- core: `AsyncEvents.fire(event)` calls `fireAsync(event, NotificationOptions.ofExecutor(executor))`. The handlers use
  it instead of `Event<…>`.
- core names the executor with the qualifier `@EventExecutor` and only sees a plain `java.util.concurrent.Executor`.
  Core stays free of threading technology.
- `inbound-event` (`EventExecutorProducer`) produces it as Quarkus' `@VirtualThreads ExecutorService`.

The observers of one event still run one after another, now on one virtual thread per event.

---

## Guards

- **ArchUnit** (`ArchitectureTest`):
  - `entry_points_run_on_virtual_threads`: every REST, Kafka and scheduler entry point carries `@RunOnVirtualThread`
    (on the method or its class). Exempt: methods returning `Uni`/`Multi`, and interfaces (REST clients carry
    `@POST` too, but are outbound). Without the annotation, Quarkus would silently fall back to a platform thread.
  - `nothing_runs_on_platform_worker_threads`: no `@Blocking`.
  - `async_events_are_fired_only_through_async_events`: no direct `Event.fireAsync` outside `AsyncEvents`.
- **Tests:**
  - `KafkaTransientFailureTest.messages_are_processed_one_at_a_time_in_order`: verified to fail without the
    concurrency setting.
  - `AsyncEventsTest`: an async observer runs on a virtual thread.
- **Pinning check** (once, 2026-10-06): the full app-server suite ran with JFR and
  `jdk.VirtualThreadPinned#threshold=0ms`. The recording had about 1,700 events from virtual threads and **no pinning
  events**. Java 25 no longer pins on `synchronized` (JEP 491). To repeat the check:

  ```bash
  mvn test -pl app-server "-DargLine=-XX:StartFlightRecording=filename=/tmp/pin.jfr,dumponexit=true,jdk.VirtualThreadPinned#enabled=true,jdk.VirtualThreadPinned#threshold=0ms,jdk.VirtualThreadPinned#stackTrace=true"
  jfr print --events jdk.VirtualThreadPinned /tmp/pin.jfr
  ```

---

## Limits

- **Concurrency limits moved to the pools:** HTTP requests no longer queue for a worker thread. The database connection
  pool (Agroal, default 20 connections) is now what limits concurrent database work. That's irrelevant at demo load.
- **Java 27:** Quarkus 3.33 can't read Java 27 bytecode ("Unsupported class file major version 71"), so the project
  targets Java 25. Running on a Java 27 JDK with target 25 works.
