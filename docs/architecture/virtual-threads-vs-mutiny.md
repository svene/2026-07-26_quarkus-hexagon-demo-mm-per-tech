# Virtual Threads vs. Mutiny: the Programming Model of a Modern Quarkus App

General background on when a Quarkus application should use Mutiny (`Uni`/`Multi`) and when plain blocking
code on virtual threads. Project-independent on purpose. Two companion files:

- [`virtual-threads-migrating-from-mutiny.md`](virtual-threads-migrating-from-mutiny.md): moving a large code base that
  uses Mutiny everywhere to virtual threads - strategy, rules, pitfalls.
- [`virtual-threads-in-this-project.md`](virtual-threads-in-this-project.md): how this demo applies it.

Written 2026-10-06 against Quarkus 3.33 and Java 25. Structured concurrency section added 2026-10-07 (Java 25-27).

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
- **Fan-out/fan-in** within one request (call three services at once, combine the results), **for now**.
  `Uni.combine().all()` is still the most concise option on Java 25. The Java replacement, structured concurrency
  (`StructuredTaskScope`), is a preview API up to Java 27. See
  [Fan-out/fan-in: structured concurrency instead of `Uni.combine()`](#fan-outfan-in-structured-concurrency-instead-of-unicombine).
- **Truly high fan-in event-loop code** where even a virtual thread per event is too much (rare in business systems).

---

## Fan-out/fan-in: structured concurrency instead of `Uni.combine()`

Virtual threads make a *sequential* flow simple. If one request needs several independent calls at the same time,
plain blocking code needs a way to start them in parallel, wait for all of them (or the first one), cancel the rest on
failure and enforce a deadline. Mutiny does that with `Uni.combine()` and `Multi`. The JDK's answer is **structured
concurrency**: `java.util.concurrent.StructuredTaskScope` forks each call as a subtask on its own virtual thread, and
the scope is a `try`-with-resources block. No subtask outlives the block.

This demo has no fan-out (each handler does its calls one after another), so nothing in it would change. The snippets
below are what a Mutiny-heavy code base would replace.

### Status

| Java | JEP | State |
|---|---|---|
| 25 (LTS) | [505](https://openjdk.org/jeps/505) | fifth preview |
| 26 | [525](https://openjdk.org/jeps/525) | sixth preview, API renamed in places |
| 27 | [533](https://openjdk.org/jeps/533) | seventh preview, exceptions reworked |
| 28 (March 2027) | [543](https://openjdk.org/jeps/543) | proposed to become final, **unchanged from 27** |

So the API shown below is the **Java 27 one, which should become final in Java 28**. Java 28 is not an LTS release. Teams
that only use LTS versions get the final API with Java 29 (September 2027). The Java 25 differences are listed
[further down](#what-is-different-in-java-25).

### The snippets assume

- On the Mutiny side, the `Uni`s come from reactive clients and so already run concurrently. (A `Uni` that wraps a
  blocking call only runs in parallel with `.runSubscriptionOn(executor)`.)
- On the structured concurrency side, the method runs on a virtual thread (`@RunOnVirtualThread`) and the clients are
  blocking. Each `fork` starts a new virtual thread.

### 1. Call a fixed set of services, combine the results

A product page needs the product from the catalogue and its stock levels.

```java
// Mutiny
public Uni<ProductView> product(String id) {
    return Uni.combine().all()
        .unis(catalog.find(id), inventory.levels(id))
        .with((product, levels) -> new ProductView(product, levels));
}
```

```java
// structured concurrency (Java 27/28)
@RunOnVirtualThread
public ProductView product(String id) throws ExecutionException, InterruptedException {
    try (var scope = StructuredTaskScope.open()) {
        Subtask<Product> product = scope.fork(() -> catalog.find(id));
        Subtask<StockLevels> levels = scope.fork(() -> inventory.levels(id));
        scope.join();                            // waits for both; throws if one failed
        return new ProductView(product.get(), levels.get());
    }
}
```

`open()` without arguments uses the default policy: wait until all subtasks succeed. If one fails, the scope
**interrupts the others** and `join()` throws an `ExecutionException` with the subtask's exception as its cause.
Mutiny's default is also fail-fast: the first failure fails the combined `Uni` and cancels the other subscriptions.
(Mutiny's `.collectFailures()`, which waits for all and reports all failures, corresponds to a custom `Joiner` or
`Joiner.allUntil(...)`.)

### 2. Same call to many services, all results

Ask every supplier for a quote and take the cheapest, within two seconds.

```java
// Mutiny
public Uni<Quote> cheapestQuote(Order order) {
    List<Uni<Quote>> quotes = suppliers.stream().map(s -> s.quote(order)).toList();
    return Uni.combine().all().unis(quotes)
        .with(Quote.class, all -> all.stream().min(comparing(Quote::price)).orElseThrow())
        .ifNoItem().after(Duration.ofSeconds(2)).fail();
}
```

```java
// structured concurrency (Java 27/28)
public Quote cheapestQuote(Order order) throws ExecutionException, InterruptedException {
    try (var scope = StructuredTaskScope.open(Joiner.<Quote>allSuccessfulOrThrow(),
                                              cf -> cf.withTimeout(Duration.ofSeconds(2)))) {
        suppliers.forEach(s -> scope.fork(() -> s.quote(order)));
        return scope.join().stream().min(comparing(Quote::price)).orElseThrow();
    }
}
```

`allSuccessfulOrThrow()` makes `join()` return the results as a `List`. The timeout covers the whole scope. When it
expires, the open subtasks are interrupted and `join()` throws an `ExecutionException` whose cause is a
`CancelledByTimeoutException`. Mutiny's `ifNoItem().after(...).fail()` throws a `TimeoutException` and cancels the
subscription. Whether the HTTP call underneath really stops depends on the client in both cases. A blocking client
stops when its thread is interrupted only if its I/O reacts to interrupts.

### 3. First successful answer wins

Several carriers can take a shipment; use the first one that confirms.

```java
// Mutiny
public Uni<Confirmation> book(Shipment shipment) {
    return Uni.combine().any().of(carriers.stream().map(c -> c.book(shipment)).toList());
}
```

```java
// structured concurrency (Java 27/28)
public Confirmation book(Shipment shipment) throws ExecutionException, InterruptedException {
    try (var scope = StructuredTaskScope.open(Joiner.<Confirmation>anySuccessfulOrThrow())) {
        carriers.forEach(c -> scope.fork(() -> c.book(shipment)));
        return scope.join();                    // first success; the others are interrupted
    }
}
```

**The semantics differ:** `Uni.combine().any()` forwards the **first event**, so a fast failure wins over a slow
success. `anySuccessfulOrThrow()` waits for the **first success** and only fails when all subtasks have failed. For
Mutiny's behaviour, add a `.onFailure().recoverWithUni(...)` per `Uni`. Usually the structured version is what was
meant.

### 4. Many calls with limited concurrency

Fetch the prices of 500 products, at most 4 requests at a time.

```java
// Mutiny
public Uni<List<Price>> prices(List<String> ids) {
    return Multi.createFrom().iterable(ids)
        .onItem().transformToUni(id -> pricing.price(id)).merge(4)
        .collect().asList();
}
```

```java
// structured concurrency (Java 27/28)
public List<Price> prices(List<String> ids) throws ExecutionException, InterruptedException {
    var permits = new Semaphore(4);
    try (var scope = StructuredTaskScope.open(Joiner.<Price>allSuccessfulOrThrow())) {
        for (var id : ids) {
            scope.fork(() -> {
                permits.acquire();
                try { return pricing.price(id); } finally { permits.release(); }
            });
        }
        return scope.join();
    }
}
```

The scope has no concurrency limit of its own. It starts 500 virtual threads, which is cheap, and the semaphore
limits how many of them call the service at once. This is the one case where Mutiny stays shorter: `merge(4)` is the
limit. Mutiny's `merge` also emits in completion order (`concatenate()` keeps the input order, but runs one call at a
time). If the order matters in the structured version, keep the `Subtask`s in a list and read them in that order.

### What gets better

- **Plain control flow inside each subtask:** a subtask is ordinary blocking code with `if`, loops and `try/catch`.
  In a `Uni` chain, every step inside a combined call is again an operator chain.
- **Cancellation is part of the structure:** when the scope fails or times out, the remaining subtasks are interrupted
  and `close()` waits until they have ended. Nothing keeps running in the background after the method returned. With
  Mutiny a forgotten or detached subscription can.
- **Observability:** the JSON thread dump (`jcmd <pid> Thread.dump_to_file -format=json <file>`) shows the subtasks
  grouped under their scope and its owner thread.
- **Scoped values** (`ScopedValue`, final since Java 25) bound in the owner thread are visible in all subtasks.

### What doesn't come for free (in Quarkus)

These apply to every subtask, because each one is a new thread:

- **No request context, no transaction:** the CDI request context and a `@Transactional` transaction belong to the
  request's thread. A subtask can't use `@RequestScoped` beans and doesn't join the caller's transaction. Each subtask
  that touches the database takes its **own connection**, so a fan-out of three needs three connections per request
  out of the pool.
- **MDC, security identity and OpenTelemetry spans** are thread-bound. Mutiny with SmallRye Context Propagation
  carries them across callbacks. For subtasks, check what arrives and pass values explicitly (or as scoped values)
  where it matters.
- **Exceptions arrive wrapped** in an `ExecutionException`. Unwrap the cause before the exception mappers see it, or
  map `ExecutionException` itself.

### What is different in Java 25

The idea and the structure are the same, but several names and the exception handling changed afterwards:

| | Java 25 | Java 27 (expected final in 28) |
|---|---|---|
| all results | `allSuccessfulOrThrow()` → `Stream<Subtask<T>>` | `allSuccessfulOrThrow()` → `List<T>` (since 26) |
| first success | `anySuccessfulResultOrThrow()` | `anySuccessfulOrThrow()` (since 26) |
| subtask failed | `join()` throws `StructuredTaskScope.FailedException` (unchecked) | `join()` throws `ExecutionException` (checked) |
| timeout | `join()` throws `StructuredTaskScope.TimeoutException` (unchecked) | `ExecutionException` with cause `CancelledByTimeoutException` |
| configuration | `open(joiner, Function<Configuration, Configuration>)` | `open(joiner, UnaryOperator<Configuration>)`, also `open(UnaryOperator<Configuration>)` |

Example 2 in Java 25:

```java
// structured concurrency (Java 25, preview)
public Quote cheapestQuote(Order order) throws InterruptedException {
    try (var scope = StructuredTaskScope.open(Joiner.<Quote>allSuccessfulOrThrow(),
                                              cf -> cf.withTimeout(Duration.ofSeconds(2)))) {
        suppliers.forEach(s -> scope.fork(() -> s.quote(order)));
        return scope.join().map(Subtask::get).min(comparing(Quote::price)).orElseThrow();
    }
}
```

Using a preview API also means:

- `--enable-preview` for the compiler, for test runs (Surefire/Failsafe `argLine`), for `quarkus:dev` and for the
  production JVM. Whether every Quarkus build step accepts preview class files has to be tried.
- Class files compiled with preview features only run on **exactly that Java version**. Code built with Java 25
  preview features doesn't start on a Java 26 or 27 runtime. (This project currently builds for Java 25 and runs fine
  on a Java 27 JDK. With preview features it wouldn't.)
- Every upgrade until the API is final means adapting the code, as the table shows.

### Until it's final: two options on Java 25 without preview

**a) Keep `Uni.combine()` for the fan-out and wait for it on the virtual thread.** During a migration the reactive
clients are still there, so this is the obvious choice. The rest of the method is already sequential:

```java
@RunOnVirtualThread
public ProductView product(String id) {
    var view = Uni.combine().all()
        .unis(catalog.find(id), inventory.levels(id))
        .with(ProductView::new)
        .await().atMost(Duration.ofSeconds(2));
    return view;   // plain code from here on
}
```

**b) A virtual-thread executor with `Future`s**, for blocking clients:

```java
@RunOnVirtualThread
public ProductView product(String id) throws ExecutionException, InterruptedException {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        Future<Product> product = executor.submit(() -> catalog.find(id));
        Future<StockLevels> levels = executor.submit(() -> inventory.levels(id));
        return new ProductView(product.get(), levels.get());
    }
}
```

It looks like the structured version, but it isn't one. If `levels` fails early, `product.get()` still waits for the
catalogue, the failed result is noticed only afterwards, and nothing cancels the other task: `close()` waits for both
to finish. Timeouts need `get(timeout, unit)` per future and explicit `cancel(true)`. That's fine for two calls that
rarely fail and is error-prone beyond that. Once structured concurrency is final, replace both a) and b) with
`StructuredTaskScope`.

---

## Decision guide

| Code | Model |
|---|---|
| HTTP endpoint / gRPC method doing I/O | blocking + `@RunOnVirtualThread` |
| Kafka consumer, order irrelevant | blocking + `@RunOnVirtualThread` |
| Kafka consumer, order or fail-stop matters | blocking + `@RunOnVirtualThread` with max concurrency 1, or `@Blocking` |
| scheduled job | blocking + `@RunOnVirtualThread` |
| SSE, WebSocket streams, merging event sources | `Multi` |
| parallel calls inside one request | Java 25: `Uni.combine()` (awaited on the virtual thread) or a virtual-thread executor; once final: `StructuredTaskScope` |
| CPU-heavy work | bounded platform pool |

---

## Sources

- [Quarkus - Virtual thread support reference](https://quarkus.io/guides/virtual-threads)
- [Quarkus - Virtual Thread support with Reactive Messaging](https://quarkus.io/version/3.33/guides/messaging-virtual-threads)
- [Quarkus blog - Processing Kafka records on virtual threads](https://quarkus.io/blog/virtual-threads-4/)
- [JEP 444 - Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 491 - Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)
- Structured concurrency: [JEP 505 (Java 25)](https://openjdk.org/jeps/505), [JEP 525 (Java 26)](https://openjdk.org/jeps/525),
  [JEP 533 (Java 27)](https://openjdk.org/jeps/533), [JEP 543 (final, proposed for Java 28)](https://openjdk.org/jeps/543)
- [Mutiny - Combining items](https://smallrye.io/smallrye-mutiny/latest/guides/combining-items/)
