# Migrating a Mutiny-Heavy Quarkus Code Base to Virtual Threads

How a large Quarkus system that uses Mutiny (`Uni`/`Multi`) everywhere moves its request/response code to plain
blocking code on virtual threads. Project-independent on purpose. Background (the two problems Mutiny solves, what gets
simpler, what stays Mutiny, which model for which code) is in
[`virtual-threads-vs-mutiny.md`](virtual-threads-vs-mutiny.md); how this demo did it is in
[`virtual-threads-in-this-project.md`](virtual-threads-in-this-project.md).

Written 2026-10-06 against Quarkus 3.33 and Java 25.

---

## Strategy: incremental, outside in

1. **Raise the runtime first:** run on Java 24 or later (Java 25 is the current LTS). Up to Java 23, a virtual thread
   that blocks inside a `synchronized` block **pins** its carrier thread: the platform thread stays blocked, which
   defeats the purpose and can deadlock under load. JEP 491 (Java 24) removed that limitation. Many libraries (JDBC
   drivers, Kafka clients, Jackson, logging) use `synchronized` internally.
2. **New code is imperative on virtual threads:** new endpoints and consumers get `@RunOnVirtualThread` and blocking
   signatures.
3. **Convert existing code where it is touched,** endpoint by endpoint, worst-to-read chains first. Each endpoint picks
   its own model, so old and new code live side by side.
4. **Keep the reactive clients for now:** on a virtual thread you may wait for a `Uni`. Quarkus explicitly supports
   this, and Mutiny's `...AndAwait()` methods or `uni.await().atMost(...)` don't pin the carrier. So the code above the
   client becomes sequential before the client is swapped:

   ```java
   @RunOnVirtualThread
   public Response product(String name) {
       var product = reactiveRepository.findByName(name).await().atMost(Duration.ofSeconds(5));
       return product == null ? Response.status(404).build() : Response.ok(product).build();
   }
   ```
5. **Swap the libraries later, if ever:** Hibernate Reactive → Hibernate ORM, the reactive Mongo client → the classic
   one, and so on. That is optional: it removes the `await()` calls and gives `@Transactional`, but the programming
   model is already sequential without it.

## Rules for the transition

- **Never block on the event loop.** A method that still returns `Uni` runs on the event loop, so it must not call
  converted, blocking code. Convert from the entry point (endpoint, consumer) inwards, or let the entry point switch
  to `@RunOnVirtualThread` first.
- **Don't leak `Uni` into the domain.** If core interfaces currently return `Uni`, change them to plain types once
  all of their callers run on virtual threads. That is the step with the biggest payoff in readability, and the one
  that touches the most code.

## Pitfalls

- **Kafka ordering:** `@RunOnVirtualThread` on an `@Incoming` method processes messages **concurrently and out of
  order** (default max concurrency 1024 per method). If order, or a stop-on-failure strategy, matters, cap the
  concurrency to 1 (`smallrye.messaging.worker.<virtual-thread>.max-concurrency=1`, or a named worker) and verify the
  behaviour with tests.
- **The limit moves to the resource pools:** with a virtual thread per request there is no thread pool limiting
  concurrency any more, so a burst goes straight to the database connection pool and to downstream services. Size the
  pools deliberately, and use a semaphore or a rate limiter where a downstream service needs protection.
- **CPU-bound work** doesn't belong on virtual threads: they help with waiting, not with computing. Keep heavy
  computation on a bounded platform pool.
- **Thread-local caches:** libraries that cache expensive objects per thread (assuming a few long-lived pooled threads)
  create one per virtual thread instead. Watch the memory and allocation profile after the switch.
- **Remaining pinning:** native frames (JNI) and class initialisation can still pin. The JFR event
  `jdk.VirtualThreadPinned` shows where (the old `-Djdk.tracePinnedThreads` flag is gone since Java 24).
- **Thread dumps:** virtual threads are not in a classic `jstack` dump. Use
  `jcmd <pid> Thread.dump_to_file -format=json <file>`.
- **Async CDI events run on platform threads:** `Event.fireAsync(event)` delivers on CDI's default executor, in Quarkus
  the platform worker pool. Pass a virtual-thread executor: `fireAsync(event, NotificationOptions.ofExecutor(executor))`
  (Quarkus provides one as `@VirtualThreads ExecutorService`). Don't let each observer hand its work to a virtual thread
  of its own instead: ArC notifies the async observers of one event **one after another in one task**, and per-observer
  hand-offs would quietly make them run in parallel.
- **Not every extension supports virtual threads:** quarkus-cxf 3.33, for example, has no `@RunOnVirtualThread` for
  SOAP service endpoints. Check each inbound technology; an endpoint that doesn't block can stay where it is.
- **Tests that only passed by timing:** different thread scheduling can expose races in existing tests (e.g. a test
  that reacts to a log entry written just *before* the step it wants to wait for). Treat a new flaky test as a hint,
  not as noise.

---

## Sources

- [Quarkus - Virtual thread support reference](https://quarkus.io/guides/virtual-threads)
- [Quarkus - Virtual Thread support with Reactive Messaging](https://quarkus.io/version/3.33/guides/messaging-virtual-threads)
- [Quarkus blog - Processing Kafka records on virtual threads](https://quarkus.io/blog/virtual-threads-4/)
- [JEP 444 - Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 491 - Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)
