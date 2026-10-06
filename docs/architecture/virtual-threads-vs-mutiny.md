# Virtual Threads vs. Mutiny: the Programming Model of a Modern Quarkus App

General background on when a Quarkus application should use Mutiny (`Uni`/`Multi`) and when plain blocking
code on virtual threads. It also covers what changes when a large code base that uses Mutiny everywhere moves to
virtual threads. Most of this file is project-independent on purpose. How this demo applies it is in the last section,
and the decision is tracked in `PLAN.md` (`virtual-threads`).

Written 2026-10-06 against Quarkus 3.33 and Java 25.

---

## The short version

- **Not Mutiny for everything any more.** For request/response code (an HTTP call, a Kafka message, a scheduled job
  that reads and writes a database) a modern Quarkus app writes **plain blocking code and runs it on a virtual
  thread**. That code doesn't hold a platform thread while it waits, and it reads like ordinary Java.
- **Mutiny stays where data really flows as a stream:** server-sent events, WebSockets, Kafka stream processing,
  merging several sources. It also stays where a framework interface demands a `Uni`/`Multi`.
- A large "Mutiny everywhere" system can move **incrementally**, one endpoint or consumer at a time, and can even keep
  its reactive clients underneath during the transition.

---

## Two problems that Mutiny solves

Reactive libraries such as Mutiny (created for Quarkus in 2019, years before virtual threads became final in Java 21)
solve two different problems. Virtual threads only replace the first.

### 1. Waiting without holding a thread

Before virtual threads, a thread that waited for a database or an HTTP call was blocked, and platform threads are
expensive (about 1 MB of stack each; a pool has a few hundred at most). A service with many concurrent slow calls ran
out of threads long before it ran out of CPU. The reactive answer: a call returns a `Uni` right away, the thread does
other work, and a callback continues once the result arrives. Few threads (the Vert.x event loop) serve many requests.

The price is paid in every line of code:

```java
// reactive: every step that waits is a callback
public Uni<Response> checkout(Order order) {
    return inventory.lockRows(order)
        .onItem().transformToUni(rows -> rows.sufficient(order)
            ? inventory.decrement(order).replaceWith(Response.ok().build())
            : Uni.createFrom().item(Response.status(409).build()))
        .onFailure().retry().atMost(3);
}
```

**Virtual threads solve the same problem in the JVM instead of in your code.** A virtual thread that blocks is
unmounted from its carrier (platform) thread. The carrier then runs another virtual thread, and the waiting one
continues when its I/O completes. Virtual threads are cheap enough to create one per request or message. So you write
the sequential version and still don't hold a platform thread while waiting:

```java
// blocking code on a virtual thread: same efficiency while waiting
@RunOnVirtualThread
public Response checkout(Order order) {
    var rows = inventory.lockRows(order);
    if (!rows.sufficient(order)) return Response.status(409).build();
    inventory.decrement(order);
    return Response.ok().build();
}
```

### 2. Composing streams of events over time

Some data is a stream by nature: inventory changes pushed to browsers, Kafka records, ticks of a timer. Merging
sources, mapping, buffering, back-pressure, and cleaning up when a subscriber disconnects are stream operations. That's
not a threading problem, so virtual threads don't help. Without a stream library you write the queues, timers and
cancellation by hand. Mutiny's `Multi` (a relative of RxJava and RxJS) is the right tool here:

```java
return Multi.createBy().merging().streams(
    Multi.createFrom().publisher(inventoryEvents),
    Multi.createFrom().ticks().every(Duration.ofSeconds(15)).map(tick -> heartbeat));
```

### Analogy for frontend developers

| Java / Quarkus | JavaScript / Angular |
|---|---|
| `Uni` chains (`.onItem().transformToUni(...)`) | Promise chains (`.then(...).then(...)`) |
| blocking code on virtual threads | `async`/`await` (sequential-looking code, the runtime suspends it) |
| `Multi` | RxJS `Observable` |

JavaScript went from callbacks to Promise chains to `async/await`. Java has gone from blocking threads to reactive
chains to virtual threads. Angular is also moving from "RxJS for everything" to Signals for state, with RxJS kept for
real event streams. The direction is the same: the general reactive library is kept for the job it is best at,
composing streams.

---

## How Quarkus decides which thread runs your code

Quarkus picks the execution model **per method, from its signature and annotations**:

| Method | Runs on | Blocking allowed? |
|---|---|---|
| returns `Uni`/`Multi` (or `CompletionStage`) | Vert.x event loop (platform thread) | **no** - blocking there stalls every request served by that loop; Quarkus throws `BlockingOperationNotAllowedException` for known blocking APIs |
| returns a plain type, or `@Blocking` | worker pool (platform threads) | yes, but each wait holds a platform thread |
| `@RunOnVirtualThread` | a new virtual thread | **yes, and it doesn't hold a platform thread** |

This works the same way for REST endpoints, `@Incoming` Kafka methods, `@Scheduled` methods, gRPC and others.

---

## What gets simpler with virtual threads

For a code base that uses Mutiny everywhere, moving the request/response parts to virtual threads simplifies these
things:

- **Control flow is plain Java again:** `if`, `for`, `try/catch/finally` and early `return` instead of
  `transformToUni`, `onFailure().recoverWithItem`, `Multi.createFrom().iterable(...).onItem().transformToUniAndConcatenate`.
- **Transactions:** `@Transactional` with Hibernate ORM / Panache instead of `Panache.withTransaction(...)`, reactive
  sessions, and keeping track of which session belongs to which Vert.x context.
- **Stack traces and debugging:** one readable stack per request, breakpoints that stop where you expect. Reactive stack
  traces mostly show the operator machinery.
- **Context:** `ThreadLocal`, MDC logging, security context and OpenTelemetry spans just work. With reactive code they
  depend on context propagation across callbacks.
- **Error handling:** an exception propagates like an exception. With reactive code, a forgotten `subscribe` or a
  failure swallowed in a chain fails silently.
- **Tests:** call the method and assert. No `.await().indefinitely()` and no `UniAssertSubscriber`.
- **Onboarding:** new team members need Java, not a reactive operator vocabulary. Code review also gets easier: "does
  this lambda run now or later?" stops being a question.

What doesn't change: the domain logic, the APIs of the endpoints and topics, and the fault tolerance annotations
(`@Retry`, `@Timeout`, `@CircuitBreaker` work on blocking methods).

---

## What stays Mutiny

- **Streams:** SSE endpoints, WebSocket message streams, Kafka processing written as `Multi` transformations.
- **Framework interfaces** that declare `Uni`/`Multi`, for example SmallRye's `KafkaFailureHandler`.
- **Fan-out/fan-in** within one request (call three services at once, combine the results). `Uni.combine().all()` is
  still the most concise option. The Java alternative, structured concurrency (`StructuredTaskScope`), is still a
  preview API in Java 25. Until it's final, an `ExecutorService` from `Executors.newVirtualThreadPerTaskExecutor()` with
  `Future`s works, but is wordier.
- **Truly high fan-in event-loop code** where even a virtual thread per event is too much (rare in business systems).

---

## Migrating a large Mutiny-based system

### Strategy: incremental, outside in

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

### Rules for the transition

- **Never block on the event loop.** A method that still returns `Uni` runs on the event loop, so it must not call
  converted, blocking code. Convert from the entry point (endpoint, consumer) inwards, or let the entry point switch
  to `@RunOnVirtualThread` first.
- **Don't leak `Uni` into the domain.** If core interfaces currently return `Uni`, change them to plain types once
  all of their callers run on virtual threads. That is the step with the biggest payoff in readability, and the one
  that touches the most code.

### Pitfalls

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

### Decision guide

| Code | Model |
|---|---|
| HTTP endpoint / gRPC method doing I/O | blocking + `@RunOnVirtualThread` |
| Kafka consumer, order irrelevant | blocking + `@RunOnVirtualThread` |
| Kafka consumer, order or fail-stop matters | blocking + `@RunOnVirtualThread` with max concurrency 1, or `@Blocking` |
| scheduled job | blocking + `@RunOnVirtualThread` |
| SSE, WebSocket streams, merging event sources | `Multi` |
| parallel calls inside one request | `Uni.combine()` or a virtual-thread executor |
| CPU-heavy work | bounded platform pool |

---

## This demo

The demo never went reactive. Handlers, SPIs and Services return plain types; persistence is JDBC
(`outbound-postgres`), classic MongoDB Panache and CXF; Kafka receivers are `@Blocking`. Its **programming model
already is the modern one**. What's missing is the execution side: the blocking code runs on **platform worker
threads**, so every wait holds one.

Moving the demo to virtual threads therefore **doesn't make its code simpler** (there are no `Uni` chains to remove),
but it **stops blocking platform threads** while keeping the code as it is. Concretely, keeping all existing libraries:

| Part | Change |
|---|---|
| REST receivers (`inbound-http-html`, `inbound-http-jsonapi`, stubs in `external-*`) | `@RunOnVirtualThread`, no code change |
| `@Scheduled` (`DemandPeriodReceiver`, `CashpointStub`) | `@RunOnVirtualThread` |
| Kafka receivers and stubs (`@Incoming` + `@Blocking`) | `@RunOnVirtualThread` with max concurrency 1, to keep order and fail-stop (`DeadLetterOrFailStop`); to be verified by the existing Kafka tests |
| SSE stream (`InventoryEventsReceiver`) | stays `Multi`: it's a stream and holds no thread while idle |
| `DeadLetterOrFailStop` | stays: `Uni` is SmallRye's interface |
| `LeadTime` (delayed stub deliveries) | stays on Quarkus' worker-pool timer: it holds no thread while waiting |

Precondition: Java 25, done on 2026-10-06 (`maven.compiler.release` 25, `.sdkmanrc`). Quarkus 3.33 can't build Java 27
bytecode yet.

---

## Sources

- [Quarkus - Virtual thread support reference](https://quarkus.io/guides/virtual-threads)
- [Quarkus - Virtual Thread support with Reactive Messaging](https://quarkus.io/version/3.33/guides/messaging-virtual-threads)
- [Quarkus blog - Processing Kafka records on virtual threads](https://quarkus.io/blog/virtual-threads-4/)
- [JEP 444 - Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 491 - Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)
