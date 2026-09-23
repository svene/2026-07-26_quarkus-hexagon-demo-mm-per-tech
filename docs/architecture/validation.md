# Input Validation

How untrusted input is validated at this system's inbound boundaries, and the one pattern used to
do it everywhere. Unlike `architecture-flow.md`/`architecture-module-participants.md`, this file
describes a **convention to follow**, not just the current state of every endpoint (see
[`wip_validation.md`](wip_validation.md) for rollout status).

This document has two parts: **Part 1 — Usage** describes the mechanism as it stands, and how to
use it. **Part 2 — Background & Reasoning** explains why it was built this way and what
alternatives were rejected.

---

# Part 1 — Usage

## The shared building block: "parse, don't validate"

Every validated boundary in this codebase (Kafka consumer or HTTP endpoint) uses the same
mechanism for a domain type `Xxx`:

- `Xxx` is a record whose components carry Jakarta Bean Validation constraint annotations
  (`@NotBlank`, `@Min`, `@Max`, …), declared exactly once.
- `Xxx` implements a sibling sealed interface `ParsedXxx` **directly** — there is no separate
  `Valid` wrapper type. The failure case has its own type, `ParsedXxx.Invalid`, holding
  `Set<ConstraintViolation<Xxx>>`.
- A private `validate(...)` helper calls
  `Validator.forExecutables().validateConstructorParameters(canonicalConstructor, args)` against the
  record's own canonical constructor (found once via
  `Class.getDeclaredConstructors()[0]`, cached in a `static final Constructor<Xxx>` field). This
  validates every constrained component in one call, driven entirely by reflection over the actual
  constructor's annotations — there is no per-property name to keep in sync with the record's
  components. This helper is used by **both**:
  - the compact constructor (throws `IllegalArgumentException` on violation — for trusted data
    only, see "Constructor vs. `parse()`" below), and
  - `parse(...)` (the normal entry point — returns `ParsedXxx.Invalid` on violation instead of
    throwing).
- Callers `switch` exhaustively over the sealed result. **What happens in the invalid branch is
  the only thing that differs by boundary** — see below.

## The decision: what the invalid branch does, by boundary

| Boundary | Synchronous caller? | Invalid branch does |
|---|---|---|
| HTTP/REST endpoint (`inbound-http-jsonapi`, `inbound-http-html`) | Yes — the HTTP client | Build a `400 Response` from the violations, right there in the resource method |
| Kafka consumer (`@Incoming`, `inbound-kafka`) | No — nobody to reject a message to | Log to the audit trail and keep consuming |

---

## Reference example: Kafka boundary

`feature.fruit` (`FruitDelivery.java`, `ParsedFruitDelivery.java`, `FruitDeliveryReceiver.java`,
all in `core`/`inbound-kafka`).

```java
public record FruitDelivery(@NotBlank String productName, @Min(1) @Max(MAX_QUANTITY) int quantity)
        implements ParsedFruitDelivery {
    // ... validate() helper, throwing compact constructor, parse() — see "shared building block" above
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
    // ... same validate()/throwing constructor/parse() shape as FruitDelivery
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

A `400` response body is a plain JSON array of violation messages, e.g. `["must not be blank"]`.

The request's *structure* is checked before `parse()`, since `parse()` only sees values: an empty
body (Quarkus passes `request == null`) is a `400` `["request body is required"]` on every endpoint,
and `/purchase` additionally rejects a missing list (`["items is required"]`) and `null` entries
(`["items[1]: must not be null"]`). An explicit empty list `{"items": []}` is not an error — see
"Multi-item input" below.

### Verified behavior

`ProductApiReceiverTest` (`app-server`) covers the accept and both reject paths:
`order_fruits_calls_supplier_and_returns_204` (valid input → `204`),
`order_fruits_with_non_positive_quantity_returns_400` (quantity `0` → `400`, body contains
`"must be greater than or equal to 1"`), and `order_fruits_with_blank_product_name_returns_400`
(blank `productName` → `400`, body contains `"must not be blank"`).

---

## `FruitsAPI`: the same construction guarantee as `InventoryAPI`

`FruitsAPI.order` takes `FruitOrder` instead of raw primitives:

```java
public interface FruitsAPI {
    void order(FruitOrder fruitOrder);
}
```

`FruitsHandler.order(FruitOrder fruitOrder)` can only ever be called with an
already-guaranteed-valid `FruitOrder`, since there's no way to construct an invalid one — the same
guarantee `InventoryAPI.updateFruitAmount(FruitDelivery fruitDelivery)` already had on the Kafka
side.

`FruitSupplierSPI` (the outbound port to the external supplier, also defined in `core`) gets the
same treatment: `FruitSupplierSPI.placeOrder(FruitOrder fruitOrder)` — `FruitsHandler` passes the
already-validated `FruitOrder` straight through instead of re-unpacking it into primitives. Only
the adapter implementing it (`FruitSupplierService` in `outbound-httpclient`) unpacks
`productName`/`quantity` at the very last step, to build the REST client's own `OrderRequest` wire
type.

`AdminReceiver`'s HTML forms (`@FormParam` inputs, `inbound-http-html`) use the same `switch` over
`XxxOrder.parse(...)`; the `Invalid` branch returns a `400` HTML fragment (`orderErrors.html`, one
line per violation message). Each form targets the `<p class="order-error">` right below it
(`hx-target="next .order-error"`), and htmx 4 swaps `4xx` responses by default, so the message
appears under the form that caused it. A successful htmx order returns `200` with an empty body
rather than `204` — htmx never swaps a `204`, and the empty swap is what clears a previous error.

## Multi-item input: `Purchase`

`/api/products/purchase`, the `cashpoint-purchases` Kafka topic, and `/shop/checkout` all carry a
*list* of items. The item itself follows the usual shape — `PurchaseItem(@NotBlank productName,
@Min(1) quantity) implements ParsedPurchaseItem` — and a small aggregate on top makes the whole
request **all-or-nothing**:

```java
public record Purchase(List<PurchaseItem> items) implements ParsedPurchase {
    public static ParsedPurchase parse(List<ParsedPurchaseItem> parsedItems) { ... }
}

record Invalid(SortedMap<Integer, Set<ConstraintViolation<PurchaseItem>>> violationsByItemIndex)
        implements ParsedPurchase {
    public List<String> messages() { ... }   // e.g. "items[1]: must be greater than or equal to 1"
}
```

The adapter parses each raw item with `PurchaseItem.parse(...)` and hands the list to
`Purchase.parse(...)`, which yields a `Purchase` only if **every** item is valid; otherwise an
`Invalid` keyed by the offending item's index. `Purchase` has no constraints of its own: its items
are valid by construction, so it is too. `PurchaseAPI.purchase(Purchase)` therefore only ever sees a
fully valid basket — one bad item rejects the whole request, nothing is deducted.

The invalid branch follows the usual boundary split:

- `ProductApiReceiver.purchase` — `400`, body is `invalid.messages()` as a JSON array.
- `CashpointReceiver` (Kafka) — audit log `"CashpointReceiver: PURCHASE_RECEIVED"` with
  `"INVALID: <items>: <messages>"`, then keep consuming.
- `ShopReceiver.checkout` — re-renders the shop page with status `400` and a list of errors, each
  prefixed with the **product name** rather than `items[i]` (a shop user can't map an index to a
  row). Rows with a blank or `0` quantity are filtered out *before* parsing: that is cart semantics
  ("not in the cart"), not validation. A non-numeric quantity can't reach `parse(String, int)`, so
  the adapter reports it itself.

An empty item list is not a violation — it parses to an empty `Purchase`, which is a harmless no-op.

## Constructor vs. `parse()`

A record's canonical constructor must be as accessible as the record itself, so the throwing
constructor of every `XxxOrder`/`XxxDelivery` is necessarily `public`. The rule for which one to
call is about **where the data comes from**, not about the constructor itself:

- **Untrusted input** — anything an inbound adapter receives (Kafka message, JSON body, HTML form)
  — must go through `parse(...)`.
- **Trusted data** — tests, or core code rebuilding a value it has already validated — may call the
  constructor directly. It still enforces the same constraints, so an invalid instance can never
  exist; it just reports a violation as an exception instead of an `Invalid` value.

The first rule is enforced by `ArchitectureTest.receivers_construct_domain_values_only_via_parse`
(`app-server`): no `*Receiver` class may call the constructor of any type implementing a
`Parsed*` interface. Deliberately not `@Deprecated` — nothing about the constructor is deprecated,
and a compiler warning would not fail the build.

---

## Where `quarkus-hibernate-validator` is declared

Any module whose own classes reference `jakarta.validation.*` directly declares
`quarkus-hibernate-validator` explicitly in its `pom.xml`:

- `core/pom.xml` — needed by `FruitDelivery`/`ParsedFruitDelivery`/`FruitOrder`/`ParsedFruitOrder`
  (`Validator`, `ConstraintViolation`, `@NotBlank`/`@Min`/`@Max`).
- `inbound-http-jsonapi/pom.xml` — needed by `ProductApiReceiver` (`ConstraintViolation`, to read
  back violation messages for the `400` body). `Requests.java` itself needs nothing here anymore —
  its DTOs carry no constraint annotations.
- `inbound-http-html/pom.xml` — needed by `AdminReceiver` (`ConstraintViolation`, same reason).

Both entries are plain (non-test-scoped) dependencies: `FruitDelivery.parse()`/`FruitOrder.parse()`
call `Validation.buildDefaultValidatorFactory()` at real application runtime, not just from tests.

---

# Part 2 — Background & Reasoning

## Why no `Valid` wrapper type

`Xxx`'s own compact constructor already throws on invalid input, so any `Xxx` instance that exists
anywhere is valid by construction; a wrapper promising "this is a validated `Xxx`" would be
redundant with a guarantee the record already gives for free. That's why only the failure case
gets a dedicated type (`ParsedXxx.Invalid`). It holds `Set<ConstraintViolation<Xxx>>` rather than
`List<String>` to keep the property path, message, and invalid value available to whoever ends up
handling it, instead of baking a message format into the domain type.

## Why `validate(...)` uses `validateConstructorParameters`, not `validate`

A plain `validator.validate(instance)` can't be used here, since the compact constructor already
throws before an "invalid instance" could ever exist to hand to it — validation has to happen
*before* construction. `ExecutableValidator.validateConstructorParameters(constructor, args)` is
Jakarta Bean Validation's purpose-built answer for exactly that: it validates candidate constructor
arguments against the constraints declared on that constructor's parameters, without ever invoking
the constructor or needing an instance.

An earlier version of `validate(...)` used `Validator.validateValue(Xxx.class, "<property>",
value)` instead — which also validates without an instance, but only one *named* property per call.
That meant `validate(...)` had to repeat every component's name as a string (`"productName"`,
`"quantity"`, …), duplicated against the record's own component list: add, rename, or remove a
constrained component and `validate(...)` silently stopped checking it unless someone remembered to
update the string list too. `validateConstructorParameters` reads the constraint annotations
directly off the constructor's parameters via reflection, so `validate(...)` needs no per-property
list at all — nothing to fall out of sync when the record's components change. This depends on
`-parameters` being enabled on the compiler (it is, project-wide, in the root `pom.xml`), so
constructor parameter names resolve to real component names (`productName`) rather than `arg0`,
`arg1`, … in violation property paths.

## Why `ParsedXxx` must be a separate top-level file

`ParsedXxx` has to be a separate top-level file, not nested inside `Xxx` itself —
`record Xxx(...) implements Xxx.ParsedXxx` (a type implementing its own inner interface) hits a
real javac limitation ("cyclic inheritance"), even though the reverse (`Invalid` implementing its
enclosing `ParsedXxx`) is fine.

## Known trade-off: `Xxx implements ParsedXxx`

Giving a plain domain record a role in its own "parse result" plumbing can look a little unusual —
a purist might expect the domain type to know nothing about how its own parsing outcome is
represented. This shape was reached only after three iterations that tried to avoid it, for the
Kafka case (`FruitDelivery`) first:

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
   own "valid" case. `FruitOrder` (the HTTP-side example) reuses this exact shape.

See [`wip_validation.md`](wip_validation.md) for what to do when extending this pattern further.

## Why the invalid-branch decision hinges on "synchronous caller?"

A REST endpoint can hand an error straight back to whoever made the request — `400 Bad Request` is
exactly the right response. A Kafka consumer has no such caller: throwing there is risky (depending
on the failure strategy it can stall the consumer or endlessly retry a poison message), and "this
message is invalid, log it and move on" is a **normal business outcome**, not an error condition —
exactly what the sealed result models.

## Why not Quarkus's declarative `@Valid`

An earlier version of the HTTP pattern used Jakarta Bean Validation's `@Valid` directly on a JSON
request DTO, letting `quarkus-rest`/`quarkus-hibernate-validator` auto-reject with `400` — no
hand-written code at all. That's still a perfectly good, simpler choice **when there's no existing
domain type whose constraints would otherwise be duplicated**. It was dropped here specifically
because `FruitOrder` (the domain type `fruitsAPI.order(...)` needs anyway) already had to carry the
same `@NotBlank`/`@Min` constraints for its own construction guarantee; keeping `@Valid` on a
second, separate request-DTO record would have meant declaring the same rule twice. Reusing
`FruitOrder.parse()` at the HTTP boundary removes that duplication entirely.

This also isn't just a design preference: Quarkus's Bean Validation is explicitly **not** wired up
for reactive-messaging `@Incoming` consumers, so it was never an option on the Kafka side to begin
with. From the Quarkus team, discussing combining Hibernate Validator with reactive code paths:

> "it's complicated, and Hibernate Validator/Bean Validation was not built for this."
> — [quarkusio/quarkus discussion #32275](https://github.com/quarkusio/quarkus/discussions/32275)

## Why the `400` body is a plain array, not a structured report

Less structured than Hibernate Validator's own violation report, but sufficient here, and it keeps
the mapping from `Invalid` → HTTP response fully explicit and local to this one method rather than
depending on framework-wired exception handling.

## Why not just deserialize the JSON body directly into `FruitOrder`?

This looks tempting — it would let Jackson call `FruitOrder`'s constructor straight from the
request body, skipping `Requests.FruitOrderRequest` entirely. It was rejected: a validation failure
there happens *during deserialization*, as a Jackson `ValueInstantiationException` wrapping the
constructor's `IllegalArgumentException` — and Quarkus's `rest-jackson` extension only ships a
built-in `400` mapper for `MismatchedInputException` (structurally malformed JSON), not for
`ValueInstantiationException`. Without a bespoke `ExceptionMapper`, this would silently fall
through to a generic, unhelpful `500`. Keeping a separate (bare) `FruitOrderRequest` and calling
`FruitOrder.parse()` explicitly avoids needing any new exception-handling infrastructure at all —
the `switch` handles both outcomes directly, so there's nothing for a mapper to catch.

## Why `quarkus-hibernate-validator` is declared per-module instead of relying on transitivity

This follows this project's "declare what you use" convention: every module that imports
`jakarta.validation` (`core`, `inbound-http-jsonapi`, `inbound-http-html`) declares the extension
itself instead of getting it transitively through `core`. The same holds for Jackson: `core` has no
Jackson dependency at all, and each adapter module that (de)serializes JSON declares its own
(`quarkus-rest-jackson`, `quarkus-rest-client-jackson` or `quarkus-jackson`).

---

## References

- [Quarkus – Validation with Hibernate Validator](https://quarkus.io/guides/validation)
- [quarkusio/quarkus discussion #32275 — Hibernate Validator and reactive](https://github.com/quarkusio/quarkus/discussions/32275)
