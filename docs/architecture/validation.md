# Input Validation

How untrusted input is validated at this system's inbound boundaries, and the one pattern used to
do it everywhere. Unlike `architecture-flow.md`/`architecture-module-participants.md`, this file
describes a **convention to follow**, not just the current state of every endpoint — most
endpoints have not adopted it yet (see "Rollout status" below).

---

## The shared building block: "parse, don't validate"

Every validated boundary in this codebase (Kafka consumer or HTTP endpoint) uses the same
mechanism for a domain type `Xxx`:

- `Xxx` is a record whose components carry Jakarta Bean Validation constraint annotations
  (`@NotBlank`, `@Min`, `@Max`, …) — the single source of truth for the rule, declared exactly
  once.
- `Xxx` implements a sibling sealed interface `ParsedXxx` **directly** — there is no separate
  `Valid` wrapper type. `Xxx`'s own compact constructor already throws on invalid input, so any
  `Xxx` instance that exists anywhere is valid by construction; a wrapper promising "this is a
  validated `Xxx`" would be redundant with a guarantee the record already gives for free. Only the
  failure case needs a dedicated type: `ParsedXxx.Invalid`, holding
  `Set<ConstraintViolation<Xxx>>` (not `List<String>` — this keeps the property path, message, and
  invalid value available to whoever ends up handling it, rather than baking a message format into
  the domain type).
- `ParsedXxx` has to be a **separate top-level file**, not nested inside `Xxx` itself —
  `record Xxx(...) implements Xxx.ParsedXxx` (a type implementing its own inner interface) hits a
  real javac limitation ("cyclic inheritance"), even though the reverse (`Invalid` implementing
  its enclosing `ParsedXxx`) is fine.
- A private `validate(...)` helper calls `Validator.validateValue(Xxx.class, "<property>", value)`
  for each constrained property and merges the results — this validates a raw value against a
  property's declared constraints **without needing an instance**, which is what makes it possible
  to validate *before* construction (a plain `validator.validate(instance)` can't be used here,
  since the compact constructor already throws before an "invalid instance" could ever exist to
  hand to it). This helper is used by **both**:
  - the compact constructor (`@Deprecated` — a backstop for callers with no graceful way to react
    to a violation, e.g. Jackson deserialization or an HTML form with no upstream check of its
    own — throws `IllegalArgumentException` on violation), and
  - `parse(...)` (the normal entry point — returns `ParsedXxx.Invalid` on violation instead of
    throwing).
- Callers `switch` exhaustively over the sealed result. Because the interface is `sealed`, the
  compiler enforces that both cases are handled — there's no way to silently forget the invalid
  branch. **What happens in the invalid branch is the only thing that differs by boundary** — see
  below.

### Known trade-off

`Xxx implements ParsedXxx` gives a plain domain record a role in its own "parse result" plumbing,
which can look a little unusual for a domain class — a purist might expect the domain type to know
nothing about how its own parsing outcome is represented. This shape was reached only after three
iterations that tried to avoid it, for the Kafka case (`FruitDelivery`) first:

1. A generic `Parsed<T>` (`Valid<T>`/`Invalid<T>`) shared across commodities — rejected because
   Java can't give a `public` record a constructor more restrictive than the record itself, so
   there was no way to stop other code calling `new Parsed.Valid<>(unvalidatedValue)` while
   keeping `Valid` visible for pattern matching outside its package.
2. A non-generic `FruitDelivery.Valid`/`FruitDelivery.Invalid`, with `Valid`/`Invalid` as plain
   classes with private constructors reachable only from `FruitDelivery.parse()` — technically
   sound (Java's private-access rule is scoped to the whole enclosing top-level type, not just
   the immediate class), but too much boilerplate to repeat across seven commodities for no
   benefit beyond what the record's own constructor already gives.
3. **Landed here**: no wrapper at all, accepting that the domain record plays double duty as its
   own "valid" case. `FruitOrder` (the HTTP-side example below) reuses this exact shape.

If this pattern is applied further (the other six commodities, or the other six `order-*`
endpoints — see "Rollout status"), replicate step 3's shape directly. Don't reintroduce a generic
`Parsed<T>` or a private-constructor wrapper class; both were explicitly tried and rejected for
this codebase.

---

## The decision: what the invalid branch does, by boundary

The `parse()`/sealed-result mechanism above is identical everywhere. What differs is **only** the
reaction to `Invalid`, and the deciding question is: **is there a synchronous caller waiting for a
response?**

| Boundary | Synchronous caller? | Invalid branch does |
|---|---|---|
| HTTP/REST endpoint (`inbound-http-jsonapi`, `inbound-http-html`) | Yes — the HTTP client | Build a `400 Response` from the violations, right there in the resource method |
| Kafka consumer (`@Incoming`, `inbound-kafka`) | No — nobody to reject a message to | Log to the audit trail and keep consuming — invalid input is a normal, handled outcome, never an exception |

A REST endpoint can hand an error straight back to whoever made the request — `400 Bad Request` is
exactly the right response. A Kafka consumer has no such caller: throwing there is risky (depending
on the failure strategy it can stall the consumer or endlessly retry a poison message), and "this
message is invalid, log it and move on" is a **normal business outcome**, not an error condition —
exactly what the sealed result models.

**On Quarkus's declarative `@Valid`**: an earlier version of the HTTP pattern used Jakarta Bean
Validation's `@Valid` directly on a JSON request DTO, letting `quarkus-rest`/`quarkus-hibernate-validator`
auto-reject with `400` — no hand-written code at all. That's still a perfectly good, simpler choice
**when there's no existing domain type whose constraints would otherwise be duplicated**. It was
dropped here specifically because `FruitOrder` (the domain type `fruitsAPI.order(...)` needs
anyway — see below) already had to carry the same `@NotBlank`/`@Min` constraints for its own
construction guarantee; keeping `@Valid` on a second, separate request-DTO record would have meant
declaring the same rule twice. Reusing `FruitOrder.parse()` at the HTTP boundary removes that
duplication entirely.

This also isn't just a design preference: Quarkus's Bean Validation is explicitly **not** wired up
for reactive-messaging `@Incoming` consumers, so it was never an option on the Kafka side to begin
with. From the Quarkus team, discussing combining Hibernate Validator with reactive code paths:

> "it's complicated, and Hibernate Validator/Bean Validation was not built for this."
> — [quarkusio/quarkus discussion #32275](https://github.com/quarkusio/quarkus/discussions/32275)

---

## Reference example: Kafka boundary

`feature.fruit` (`FruitDelivery.java`, `ParsedFruitDelivery.java`, `FruitDeliveryReceiver.java`,
all in `core`/`inbound-kafka`).

```java
public record FruitDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity)
        implements ParsedFruitDelivery {
    // ... validate() helper, @Deprecated compact constructor, parse() — see "shared building block" above
}
```

```java
switch (FruitDelivery.parse(message.productName(), message.quantity())) {
    case ParsedFruitDelivery.Invalid invalid -> { /* log to audit trail, keep consuming */ }
    case FruitDelivery fruitDelivery -> { /* update inventory */ }
}
```

---

## Reference example: HTTP boundary

`POST /api/products/order-fruits` — `FruitOrder`/`ParsedFruitOrder` (`core`) +
`Requests.FruitOrderRequest`/`ProductApiReceiver.orderFruits` (`inbound-http-jsonapi`).

`FruitOrder` follows the exact same shape as `FruitDelivery`:

```java
public record FruitOrder(@NotBlank String productName, @Min(1) int quantity) implements ParsedFruitOrder {
    // ... same validate()/@Deprecated constructor/parse() shape as FruitDelivery
}
```

The wire-format DTO stays a **bare, unvalidated** record — no constraint annotations, no `@Valid`:

```java
record FruitOrderRequest(String productName, int quantity) {}
```

The resource method `parse()`s straight into the domain type and reacts to the result itself,
building the `400` directly from the collected `ConstraintViolation`s:

```java
@POST
@Path("/order-fruits")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public Response orderFruits(Requests.FruitOrderRequest request) {
    return switch (FruitOrder.parse(request.productName(), request.quantity())) {
        case ParsedFruitOrder.Invalid invalid -> Response.status(Response.Status.BAD_REQUEST)
            .entity(invalid.violations().stream().map(ConstraintViolation::getMessage).toList())
            .build();
        case FruitOrder fruitOrder -> {
            fruitsAPI.order(fruitOrder);
            yield Response.noContent().build();
        }
    };
}
```

A `400` response body is a plain JSON array of violation messages, e.g. `["must not be blank"]` —
less structured than Hibernate Validator's own violation report, but sufficient here, and it keeps
the mapping from `Invalid` → HTTP response fully explicit and local to this one method rather than
depending on framework-wired exception handling.

### Why not just deserialize the JSON body directly into `FruitOrder`?

This looks tempting — it would let Jackson call `FruitOrder`'s constructor straight from the
request body, skipping `Requests.FruitOrderRequest` entirely. It was rejected: a validation failure
there happens *during deserialization*, as a Jackson `ValueInstantiationException` wrapping the
constructor's `IllegalArgumentException` — and Quarkus's `rest-jackson` extension only ships a
built-in `400` mapper for `MismatchedInputException` (structurally malformed JSON), not for
`ValueInstantiationException`. Without a bespoke `ExceptionMapper`, this would silently fall
through to a generic, unhelpful `500`. Keeping a separate (bare) `FruitOrderRequest` and calling
`FruitOrder.parse()` explicitly avoids needing any new exception-handling infrastructure at all —
the `switch` above handles both outcomes directly, so there's nothing for a mapper to catch.

### Verified behavior

`ProductApiReceiverTest` (`app-server`) covers the accept and both reject paths:
`order_fruits_calls_supplier_and_returns_204` (valid input → `204`),
`order_fruits_with_non_positive_quantity_returns_400` (quantity `0` → `400`, body contains
`"must be greater than or equal to 1"`), and `order_fruits_with_blank_product_name_returns_400`
(blank `productName` → `400`, body contains `"must not be blank"`).

---

## `FruitsAPI`: the same construction guarantee as `InventoryAPI`

Before this pattern existed, `FruitsAPI.order(String productName, int quantity)` took raw
primitives — nothing stopped any caller, validated or not, from passing bad data straight through.
This was the asymmetry that prompted the whole design: on the Kafka side,
`InventoryAPI.updateFruitAmount(FruitDelivery fruitDelivery)` can only ever be called with an
already-guaranteed-valid `FruitDelivery`, because there's no way to construct an invalid one.

`FruitsAPI.order` now takes `FruitOrder` instead:

```java
public interface FruitsAPI {
    void order(FruitOrder fruitOrder);
}
```

So `FruitsHandler.order(FruitOrder fruitOrder)` has the identical guarantee now: it's impossible
to call it with an invalid product name or quantity. The guarantee moved from "whoever remembers
to check" to "the type system won't let you construct the argument otherwise."

Since `FruitSupplierSPI` (the *outbound* port to the external supplier) is also defined in `core`,
it gets the same treatment: `FruitSupplierSPI.placeOrder(FruitOrder fruitOrder)` — `FruitsHandler`
now passes the already-validated `FruitOrder` straight through instead of re-unpacking it into
primitives. Only the adapter implementing it (`FruitSupplierService` in `outbound-httpclient`)
unpacks `productName`/`quantity` at the very last step, to build the REST client's own
`OrderRequest` wire type. So `FruitOrder` now guards the entire path from `ProductApiReceiver`
through both the inbound and outbound ports — every intermediate call is type-guaranteed valid;
only the final hop across the hexagon's boundary to an external system deals in primitives again,
which is unavoidable since that wire format is what the (simulated) external supplier expects.

One asymmetry worth noting: `AdminReceiver`'s HTML form (`@FormParam` inputs) still constructs
`FruitOrder` via the `@Deprecated` throwing constructor directly, since it has no `parse()`-based
pre-check of its own yet. This is strictly better than before (bad input used to flow straight
through to the supplier call; now it throws instead), but a violation surfaces as an unhandled
exception (no custom `ExceptionMapper` exists in this project) rather than a friendly HTML error.
Giving the admin form its own `parse()`-based handling, matching `orderFruits` above, is a possible
future improvement, not yet done.

---

## Where `quarkus-hibernate-validator` is declared

Following this project's existing "declare what you use" convention (already visible with
`quarkus-rest-jackson`, which appears in both `core` and `inbound-http-jsonapi` even though the
latter could get it transitively) — any module whose own classes reference `jakarta.validation.*`
directly declares `quarkus-hibernate-validator` explicitly in its `pom.xml`, rather than relying on
it arriving transitively through `core`:

- `core/pom.xml` — needed by `FruitDelivery`/`ParsedFruitDelivery`/`FruitOrder`/`ParsedFruitOrder`
  (`Validator`, `ConstraintViolation`, `@NotBlank`/`@Min`/`@Max`).
- `inbound-http-jsonapi/pom.xml` — needed by `ProductApiReceiver` (`ConstraintViolation`, to read
  back violation messages for the `400` body). `Requests.java` itself needs nothing here anymore —
  its DTOs carry no constraint annotations.

Both entries are plain (non-test-scoped) dependencies: `FruitDelivery.parse()`/`FruitOrder.parse()`
call `Validation.buildDefaultValidatorFactory()` at real application runtime, not just from tests,
so the actual Hibernate Validator implementation (not just the `jakarta.validation-api`
annotations) needs to be on the runtime classpath of every module that calls it directly.

---

## Rollout status

**Kafka boundary**: implemented for `feature.fruit` only. Not yet applied to
`feature.meat`/`dairy`/`bakery`/`vegetable`/`beverage`/`nonfood`, which still use their older,
unvalidated or `Optional`-returning `parse()` style (see e.g. `MeatDelivery.parse()`).

**HTTP boundary**: implemented for `POST /api/products/order-fruits` only (`FruitOrder`/
`ParsedFruitOrder`). `AdminReceiver`'s `/admin/order-fruits` HTML form also now constructs a
`FruitOrder` (so it can't reach `FruitsHandler` with invalid data either), but via the throwing
constructor rather than its own `parse()`-based handling — see above. Not yet applied to the other
six `order-*` endpoints on `ProductApiReceiver`/`AdminReceiver`, the `/purchase` endpoint, or
`ShopReceiver`, all of which still accept unvalidated quantities.

---

## References

- [`FruitDelivery.java`](../../core/src/main/java/org/svenehrke/triptychdemo/feature/fruit/FruitDelivery.java)
- [`ParsedFruitDelivery.java`](../../core/src/main/java/org/svenehrke/triptychdemo/feature/fruit/ParsedFruitDelivery.java)
- [`FruitDeliveryReceiver.java`](../../inbound-kafka/src/main/java/org/svenehrke/triptychdemo/feature/fruit/FruitDeliveryReceiver.java)
- [`FruitOrder.java`](../../core/src/main/java/org/svenehrke/triptychdemo/feature/fruit/FruitOrder.java)
- [`ParsedFruitOrder.java`](../../core/src/main/java/org/svenehrke/triptychdemo/feature/fruit/ParsedFruitOrder.java)
- [`FruitsAPI.java`](../../core/src/main/java/org/svenehrke/triptychdemo/feature/fruit/FruitsAPI.java)
- [`FruitSupplierSPI.java`](../../core/src/main/java/org/svenehrke/triptychdemo/feature/fruit/FruitSupplierSPI.java)
- [`FruitSupplierService.java`](../../outbound-httpclient/src/main/java/org/svenehrke/triptychdemo/feature/fruit/FruitSupplierService.java)
- [`Requests.java`](../../inbound-http-jsonapi/src/main/java/org/svenehrke/triptychdemo/cross/Requests.java)
- [`ProductApiReceiver.java`](../../inbound-http-jsonapi/src/main/java/org/svenehrke/triptychdemo/cross/ProductApiReceiver.java)
- [`AdminReceiver.java`](../../inbound-http-html/src/main/java/org/svenehrke/triptychdemo/cross/AdminReceiver.java)
- [Quarkus – Validation with Hibernate Validator](https://quarkus.io/guides/validation)
- [quarkusio/quarkus discussion #32275 — Hibernate Validator and reactive](https://github.com/quarkusio/quarkus/discussions/32275)
