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
- A private `validate(...)` helper validates the arguments against the parameter constraints of the
  record's own canonical constructor, without invoking it. It uses `ConstructorValidation`
  (`core`, `cross.validation`), which holds the one shared `Validator` and wraps
  `validateConstructorParameters`. The constructor is looked up by its parameter types with
  `ConstructorValidation.declaredConstructor(Xxx.class, String.class, int.class)` and cached in a
  `static final Constructor<Xxx>` field. This
  validates every constrained component in one call, driven entirely by reflection over the actual
  constructor's annotations — there is no per-property name to keep in sync with the record's
  components. This helper is used by **both**:
  - the compact constructor (throws `IllegalArgumentException` on violation — for trusted data
    only, see "Constructor vs. `parse()`" below), and
  - `parse(...)` (the normal entry point — returns `ParsedXxx.Invalid` on violation instead of
    throwing).
- **Text input** (the order types `XxxOrder` only): a second overload, `parse(String productName,
  String quantity)`, takes the quantity as text, e.g. an HTML form field. It validates the text
  against a private, never-invoked `Xxx(String, String)` constructor. That constructor carries
  `@NotBlank` and `@Pattern(message = "must be a number")`, at most 9 digits so the value always
  fits an `int`. Then it converts the text and delegates to `parse(String, int)`. So a
  non-number becomes an ordinary `ConstraintViolation<Xxx>` in the same `Invalid`, and a blank
  product name and a non-numeric quantity are reported together. See "Why a never-invoked second
  constructor for text input" in Part 2.
- Callers `switch` exhaustively over the sealed result. **What happens in the invalid branch is
  the only thing that differs by boundary** — see below.

## The decision: what the invalid branch does, by boundary

| Boundary | Synchronous caller? | Invalid branch does |
|---|---|---|
| HTTP/REST endpoint (`inbound-http-jsonapi`, `inbound-http-html`) | Yes — the HTTP client | Build a `400 Response` from the violations, right there in the resource method |
| Kafka consumer (`@Incoming`, `inbound-kafka`) | No — nobody to reject a message to | Log to the audit trail and keep consuming |

Independently of validation, every receiver audit-logs `"<Receiver>: …_RECEIVED"` with the raw
input as the first thing it does (HTTP: state-changing `POST`s only). Handlers log
`…_PROCESSING` instead, so a `…_RECEIVED` entry always comes from the inbound adapter. Invalid HTTP
input is not audit-logged beyond that receipt: the caller already gets the `400`.

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
public void receive(RawFruitDelivery message) {
    logReceived(message);   // FRUIT_DELIVERY_RECEIVED, before any validation
    validated(message).ifPresent(inventoryHandler::updateFruitAmount);
}

private Optional<FruitDelivery> validated(RawFruitDelivery message) {
    rejectUnprocessable(message);   // null payload -> UnprocessableMessageException -> DLQ
    return switch (FruitDelivery.parse(message.productName(), message.quantity())) {
        case ParsedFruitDelivery.Invalid invalid -> { /* audit-log "FruitDeliveryReceiver: INVALID" */ yield Optional.empty(); }
        case FruitDelivery fruitDelivery -> Optional.of(fruitDelivery);
    };
}
```

Every message is audit-logged as `…_DELIVERY_RECEIVED` first, purely for information. An invalid
one then gets a second entry, `"<Receiver>: INVALID"` with details `"<name>, <qty>: <violation
messages>"`, and is skipped.

A message that fails *before* `parse()` goes to the channel's dead-letter topic, `<topic>-dlq`.
This covers messages that can't be deserialized (invalid JSON, `"quantity": "abc"`), tombstones,
and cashpoint messages with `"items": null` or `null` entries. They are **not** written to the
audit log, except that a delivery receiver's `…_DELIVERY_RECEIVED` entry (logged before any check)
records a tombstone as `"null payload (tombstone)"`. The DLQ is the authoritative record of them,
and it keeps the payload so it can be replayed.

Two mechanisms work together, configured on every incoming channel with
`fail-on-deserialization-failure=false` and `failure-strategy=dead-letter-or-fail-stop` (a custom
strategy, `DeadLetterOrFailStop` in `inbound-kafka`):

- **Deserialization failures** are routed to the DLQ by SmallRye itself, with the original bytes.
  The receiver never sees them.
- **Structural problems the receiver detects** (a `null` payload, or `items` missing or containing
  `null` entries) make the receiver throw an `UnprocessableMessageException`. The message is
  nacked, and the exception message becomes the `dead-letter-reason` header.

Any *other* exception is treated as transient (for example, the database is down) and does
**not** go to the DLQ: the receiver's `@Retry` (MicroProfile Fault Tolerance, 3 retries, 1 s
apart, skipped for `UnprocessableMessageException`) tries again, and if that fails as well the
channel stops without committing the offset, so the message is processed again after a restart.
A retry runs the whole `receive()` again, so its audit entries (such as `…_DELIVERY_RECEIVED`) can
appear more than once. `KafkaTransientFailureTest` (`app-server`) covers both outcomes.

Without this, each of these cases permanently stops its channel. This was verified with a probe
before the fix. `KafkaMalformedMessageTest` (`app-server`) sends each case to a probe topic, checks
the DLQ record's value and headers, and checks that the next valid message is still processed.
The general reasoning (why a DLQ and not stopping or skipping, and how to replay records) is in
[`kafka-unprocessable-messages.md`](kafka-unprocessable-messages.md).

---

## Reference example: HTTP boundary

`POST /api/products/order-fruits` — `FruitOrder`/`ParsedFruitOrder` (`core`) +
`FruitOrderRequest`/`ProductApiReceiver.orderFruits` (`inbound-http-jsonapi`).

`FruitOrder` follows the exact same shape as `FruitDelivery`:

```java
public record FruitOrder(@NotBlank String productName, @Min(1) @Max(2000) int quantity) implements ParsedFruitOrder {
    // ... same validate()/throwing constructor/parse() shape as FruitDelivery
}
```

The wire-format DTO stays a **bare, unvalidated** record — no constraint annotations, no `@Valid`.
It only knows its own *structure*, which the domain `parse()` can't see because it only gets values:

```java
record FruitOrderRequest(String productName, Integer quantity) {
    public static List<String> structureErrors(FruitOrderRequest request) {
        if (request == null) return List.of(BODY_REQUIRED);
        var errors = new ArrayList<String>();
        if (request.productName() == null) errors.add(PRODUCT_NAME_REQUIRED);
        if (request.quantity() == null) errors.add(QUANTITY_REQUIRED);
        return errors;
    }
}
```

`structureErrors` is static because an empty body arrives as `request == null`, and it reports every
structure error, not just the first. `quantity` is an `Integer`, so a missing or `null` value stays
`null` instead of silently becoming `0`. The messages are shared in `RequestStructureErrorMessages`.

The resource method checks the structure first, then `parse()`s straight into the domain type and
reacts to the result itself, building the `400` directly from the collected `ConstraintViolation`s:

```java
@POST
@Path("/order-fruits")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public Response orderFruits(FruitOrderRequest request) {
    var structureErrors = FruitOrderRequest.structureErrors(request);
    if (!structureErrors.isEmpty()) return badRequest(structureErrors);
    return switch (FruitOrder.parse(request.productName(), request.quantity())) {
        case ParsedFruitOrder.Invalid invalid -> badRequest(messagesOf(invalid.violations()));
        case FruitOrder order -> {
            fruitsHandler.order(order);
            yield Response.noContent().build();
        }
    };
}
```

A `400` response body is a plain JSON array of messages, e.g. `["must not be blank"]`
(`JsonResponses.badRequest`).

The structure errors: an empty body is `["request body is required"]` on every endpoint, and a
missing or `null` field is `"productName is required"` / `"quantity is required"` (`{}` gets both).
A blank `productName` is not a structure error; the domain's `@NotBlank` reports it.
`PurchaseRequest.structureErrors` additionally rejects a missing list (`["items is required"]`),
`null` entries (`"items[1]: must not be null"`) and missing fields per item
(`"items[0].productName is required"`, `"items[0].quantity is required"`), collected over all items.
The per-item checks, `null` entries included, live in `PurchaseRequestItem.structureErrors(item, path)`;
`PurchaseRequest` passes each item's path (`items[i]`) and concatenates the results. An explicit empty
list `{"items": []}` is not an error — see "Multi-item input" below.

Some input never reaches the resource method, because Jackson rejects it while deserializing. Two
things on `ProductApiReceiver` handle that, and they apply to this resource only:

- `@CustomDeserialization(StrictJsonReader.class)` reads the body with a strict copy of the global
  `ObjectMapper`. Its coercion config rejects `"5"` and `5.7` for an integer and `123` for a string,
  which Jackson would otherwise accept silently. The global mapper stays lenient because the Kafka
  deserializers use it too.
- Two `@ServerExceptionMapper` methods turn Jackson's *deserialization* errors into the same JSON
  array. They must be declared in the resource class to stay resource-local, so they are 2-line
  delegates to the shared `JsonInputErrors`. Messages: `["quantity: must be an integer"]`, `["productName: must be a string"]`,
  `["items: must be a list"]`, `["items[0].quantity: number is out of range"]`,
  `["request body is not valid JSON"]`, and otherwise `["<path>: has an invalid value"]`. They
  replace Quarkus's built-in `{"objectName":…,"line":…}` body and the empty `400` for invalid JSON.

These mappers only handle deserialization errors. Validation still has no `@Valid` and no exception
mapper: the method's `switch` over `parse()` handles it.

The admin HTML forms (`AdminReceiver`) receive `quantity` as a `String` and pass it straight to
`XxxOrder.parse(String, String)`. An `int` `@FormParam` would fail in JAX-RS before the method runs,
and the caller would get Quarkus's default `400` instead of the `orderErrors` fragment. A blank
value gives `"must not be blank"`, and a value that isn't a number gives `"must be a number"`.

### Verified behavior

`ProductApiReceiverTest` (`app-server`) covers the accept and both reject paths:
`order_fruits_calls_supplier_and_returns_204` (valid input → `204`),
`order_fruits_with_non_positive_quantity_returns_400` (quantity `0` → `400`, body contains
`"must be greater than or equal to 1"`), and `order_fruits_with_blank_product_name_returns_400`
(blank `productName` → `400`, body contains `"must not be blank"`).
`missing_quantity_returns_400`, `purchase_reports_every_item_without_quantity` and
`malformed_input_returns_400_with_json_messages` cover the structure and deserialization errors,
each with its exact message.

---

## `FruitsHandler`: the same construction guarantee as `InventoryHandler`

`FruitsHandler.order` takes `FruitOrder` instead of raw primitives:

```java
public void order(FruitOrder fruitOrder) { ... }
```

`FruitsHandler.order(FruitOrder fruitOrder)` can only ever be called with an
already-guaranteed-valid `FruitOrder`, since there's no way to construct an invalid one — the same
guarantee `InventoryHandler.updateFruitAmount(FruitDelivery fruitDelivery)` already had on the Kafka
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
@Min(1) @Max(50) quantity) implements ParsedPurchaseItem` — and a small aggregate on top makes the whole
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
are valid by construction, so it is too. `PurchaseHandler` therefore only ever sees a
fully valid basket — one bad item rejects the whole request, nothing is deducted.

The invalid branch follows the usual boundary split:

- `ProductApiReceiver.purchase` — `400`, body is `invalid.messages()` as a JSON array.
- `CashpointReceiver` (Kafka) — audit log `"CashpointReceiver: INVALID"` with
  `"<items>: <messages>"`, then keep consuming. As with the delivery receivers, every message is
  first logged as `"CashpointReceiver: PURCHASE_RECEIVED"`, before any check.
- `ShopReceiver.checkout` — re-renders the shop page with status `400` and a list of errors, each
  prefixed with the **product name** rather than `items[i]` (a shop user can't map an index to a
  row). Rows with a blank or `0` quantity are filtered out *before* parsing: that is cart semantics
  ("not in the cart"), not validation. A non-numeric quantity can't reach `parse(String, int)`, so
  the adapter reports it itself.

An empty item list is not a violation — it parses to an empty `Purchase`, which is a harmless no-op.

### After parsing: stock is a business rule, not validation

A valid `Purchase` can still be impossible to fulfil: there may not be enough stock. That depends on
the current state, not on the input, so it is decided in the core, after `parse()`, and reported as
its own sealed type — not as a `ConstraintViolation`:

- **Online** (`/shop/checkout`, `/api/products/purchase`) → `PurchaseHandler.checkout(Purchase)` returns
  `PurchaseOutcome` = `Completed` | `Rejected(List<Shortage>)`. Rejected is all-or-nothing (nothing
  is deducted) and answered with `409 Conflict`, not `400`: the request was fine, the stock isn't
  there. `InventoryService.deductAll` locks each product row (`SELECT ... FOR UPDATE`) in
  product-name order inside one transaction, so concurrent customers can never both buy the last
  item, and never deadlock.
- **Physical store** (`cashpoint-purchases`) → `PurchaseHandler.recordStoreSale(Purchase)` never rejects:
  the goods have already left the shelf, so selling more than is on record means the inventory was
  wrong. Stock is capped at `0` and the gap is audit-logged as `STOCK_DISCREPANCY`.

Both go through the same SPI method, `deductAll(quantities, OnShortage)` — same locking, same
transaction; the `REJECT`/`CAP_AT_ZERO` policy is the only difference.

Upper limits *are* validation, since they only depend on the input: `@Max(2000)` per order,
`@Max(50)` per purchase item (at the cashpoint too), `@Max(10_000)` per delivery.

## Constructor vs. `parse()`

A record's canonical constructor must be as accessible as the record itself, so the throwing
constructor of every `XxxOrder`/`XxxDelivery` is necessarily `public`. The rule for which one to
call is about **where the data comes from**, not about the constructor itself:

- **Untrusted input** — anything an inbound adapter receives (Kafka message, JSON body, HTML form)
  — must go through `parse(...)`.
- **Trusted data** — tests, or core code rebuilding a value it has already validated — may call the
  constructor directly. It still enforces the same constraints, so an invalid instance can never
  exist; it just reports a violation as an exception instead of an `Invalid` value.

The first rule is enforced by `ArchitectureTest.inbound_adapters_construct_domain_values_only_via_parse`
(`app-server`): no class in an `inbound-*` module — receivers and the helpers they delegate to
alike — may call the constructor of any type implementing a `Parsed*` interface. The module is
recognized by the class file's location, since all modules share the same `feature`/`cross`
packages. Deliberately not `@Deprecated` — nothing about the constructor is deprecated, and a
compiler warning would not fail the build.

---

## Where `quarkus-hibernate-validator` is declared

Any module whose own classes reference `jakarta.validation.*` directly declares
`quarkus-hibernate-validator` explicitly in its `pom.xml`:

- `core/pom.xml` — needed by `FruitDelivery`/`ParsedFruitDelivery`/`FruitOrder`/`ParsedFruitOrder`
  (`Validator`, `ConstraintViolation`, `@NotBlank`/`@Min`/`@Max`).
- `inbound-http-jsonapi/pom.xml` — needed by `ProductApiReceiver` (`ConstraintViolation`, to read
  back violation messages for the `400` body). the request records themselves need nothing here —
  they carry no constraint annotations.
- `inbound-http-html/pom.xml` — needed by `AdminReceiver` (`ConstraintViolation`, same reason).

Both entries are plain (non-test-scoped) dependencies: `ConstructorValidation`, used by every
`parse()`, calls `Validation.buildDefaultValidatorFactory()` at real application runtime, not just
from tests. It does so once, for all records: building a `ValidatorFactory` is expensive, and a
`Validator` is thread-safe.

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

## Why a never-invoked second constructor for text input

`parse(String, String)` has to report "not a number" through the same `ParsedXxx.Invalid`, and that
holds `Set<ConstraintViolation<Xxx>>` (see "Why no `Valid` wrapper type"). A failed
`Integer.parseInt` isn't a `ConstraintViolation`, and one can't reasonably be built by hand. Two
other options were rejected:

- changing `Invalid` to `List<String>`, which gives up the property path and invalid value, and
- adding a third sealed case such as `Unparseable`, which every `switch` would then have to handle,
  including callers that can never produce it.

A second record constructor, `Xxx(String, String)`, solves it. Its parameters carry the text-level
constraints, and `validateConstructorParameters` checks them exactly as it checks the canonical
constructor, so the result is a real `ConstraintViolation<Xxx>`. The constructor is never invoked.
It is `private`, and it delegates to the canonical constructor only because records require it.
Having two constructors is also why every record looks up its constructors by parameter types.
`getDeclaredConstructors()[0]` was used before, and its order isn't guaranteed.

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
because `FruitOrder` (the domain type `fruitsHandler.order(...)` needs anyway) already had to carry the
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
request body, skipping `FruitOrderRequest` entirely. It was rejected: a validation failure
there happens *during deserialization*, as a Jackson `ValueInstantiationException` wrapping the
constructor's `IllegalArgumentException` — and Quarkus's `rest-jackson` extension only ships a
built-in `400` mapper for `MismatchedInputException` (structurally malformed JSON), not for
`ValueInstantiationException`. Without a bespoke `ExceptionMapper`, this would silently fall
through to a generic, unhelpful `500`. Keeping a separate (bare) `FruitOrderRequest` and calling
`FruitOrder.parse()` explicitly keeps validation out of exception handling —
the `switch` handles both outcomes directly, so there's nothing for a mapper to catch. (The
resource's exception mappers only handle JSON that can't be deserialized at all; see above.)

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
