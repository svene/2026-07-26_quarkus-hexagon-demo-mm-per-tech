# Architectural concepts

This document explains the conceptual decisions behind
`quarkus-hexagon-demo-mm-per-tech` and what makes it a useful proof of concept.

---

## Goal

Show that **hexagonal architecture** (also called Ports & Adapters) maps cleanly
onto a real Quarkus multi-module Maven project, and that **one module per adapter
technology** is a practical and scalable way to slice those modules.

The domain is a supermarket inventory system intentionally kept simple so the
architecture is the thing worth studying, not the business rules.

---

## Hexagonal architecture in one paragraph

The core of the application (domain model + use cases) lives in isolation. It
defines two kinds of contracts:

- **Inbound ports (API)** — interfaces that *callers* invoke to drive the
  application (REST endpoints, Kafka consumers, schedulers).
- **Outbound ports (SPI)** — interfaces that the application *calls* to reach
  outside infrastructure (databases, messaging brokers, remote APIs).

Adapters sit at the boundary. An inbound adapter translates an external event
(HTTP request, Kafka message, timer tick) into a call on an API interface. An
outbound adapter implements an SPI interface using a concrete technology
(Hibernate, MongoDB, MicroProfile REST Client, CXF).

The core never imports anything from an adapter. Adapters know about core but
not about each other.

---

## Naming conventions

| Layer | Java naming | Example |
|---|---|---|
| Inbound port | `*API` | `FruitsAPI`, `PurchaseAPI` |
| Outbound port | `*SPI` | `FruitSupplierSPI`, `InventoryRepositorySPI` |
| Use-case implementation | `*Handler` | `FruitsHandler`, `PurchaseHandler` |
| Inbound adapter | `*Receiver` | `FruitDeliveryReceiver`, `ProductApiReceiver` |
| Outbound adapter | `*Service` | `FruitSupplierService`, `InventoryService` |

---

## Module-per-technology, not module-per-domain

The key architectural decision in this project is the module cut.

**Alternative: one module per domain concept.** You could have a
`fruits`, `beverages`, `vegetables` module, each
containing everything related to that product type: the REST client, the Kafka
receiver, the database entity. This is intuitive at first. But as the number of
product categories grows (7 here, hundreds in a real system), the number of
modules explodes — and every module does the same thing, just for a different
noun.

**Chosen approach: one module per technology.** `outbound-httpclient`
handles *all* REST-client suppliers (fruits, vegetables, dairy). Adding a new
REST-based supplier means adding one class to an existing module and wiring one
Kafka channel. No new module, no new pom.xml, no new Maven dependency.

The benefits:

- **Coherence**: all code for "how to call a REST API" lives together. If the
  REST client configuration changes, there is exactly one place to change it.
- **Bounded blast radius**: a CXF upgrade affects only
  `outbound-webservice`. Everything else is untouched.
- **Reuse of technology configuration**: the Hibernate entity scanning, the CXF
  bus setup, the Kafka serializer — each configured once, used for all products.
- **Predictable growth**: adding a 10th product type adds at most one class per
  adapter module (the handler, the receiver, the SPI implementation). Adding a
  new adapter technology adds exactly one new module.

---

## The core module

`core` is plain Java + CDI. No Quarkus extensions, no framework annotations
beyond `@ApplicationScoped` and `@Inject`. This is deliberate: the domain logic
must not depend on infrastructure choices, and must be testable without a
container.

All use-case interfaces (API) and all infrastructure contracts (SPI) are declared
here. Adapter modules implement SPIs; inbound adapters call APIs.

---

## External system stubs

The `external-*` modules simulate external systems that in production would be
separate services. They run inside the same Quarkus process in dev and test —
made possible by Quarkus Dev Services and the fact that all Kafka topics are
shared inside the same Redpanda container.

**Deliberate decoupling:** stubs share no Java types with the adapters that call
them. The contract is the wire protocol:

- REST stubs: same HTTP path and JSON structure.
- SOAP stubs: same WSDL `targetNamespace` and operation name. The SEI interface
  is duplicated — one copy in `outbound-webservice`, one in
  `external-beverage-supplier-stub`. That is intentional: in production
  the stub would not exist in the same JVM at all.
- Kafka stubs: same topic name and JSON message structure.

---

## Kafka channel naming: the `-out` suffix

When a Quarkus application has both an `@Incoming("foo")` consumer **and** an
`@Channel("foo") Emitter` for the same Kafka topic, SmallRye Reactive Messaging
requires they be declared as separate channels. The convention used here is:

- `foo` — the `@Incoming` consumer channel name (matches the Kafka topic name)
- `foo-out` — the `@Channel` / outgoing emitter channel name (mapped to the same
  Kafka topic via `mp.messaging.outgoing.foo-out.topic=foo`)

This applies to all stub emitters (`fruit-deliveries-out`,
`vegetables-deliveries-out`, etc.) and to the non-food order channel
(`nonfood-orders-out`).

---

## Kafka-based supplier (non-food) vs. REST/SOAP suppliers

Fruits, vegetables, and dairy use synchronous REST. Beverages, meat, and bakery
use synchronous SOAP. Both return immediately after the order is placed; the
delivery arrives later on a Kafka topic.

The non-food supplier uses a fully asynchronous Kafka round-trip:

```
app → nonfood-orders (Kafka) → NonFoodSupplierStub
NonFoodSupplierStub → nonfood-deliveries (Kafka) → NonFoodDeliveryReceiver → InventoryHandler
```

This illustrates that the same hexagonal boundary works regardless of whether the
outbound call is synchronous (REST/SOAP) or asynchronous (Kafka). The
`NonFoodSupplierSPI` interface in core is identical in shape to
`FruitSupplierSPI` — the asynchrony is entirely hidden inside the adapter.

---

## Audit log and the single-responsibility rule

Only `core` writes to the audit log. Inbound adapters (Kafka receivers, REST
endpoints) never call `AuditLogSPI` directly — they call an API method, and the
handler logs inside its own boundary. This ensures the audit trail reflects
**what the application decided to do**, not the raw events it received.

---

## Customer checkout: external stub → Kafka → inbound adapter

The customer purchase flow demonstrates the full event-driven path:

```
CashpointStub (scheduler) → cashpoint-purchases (Kafka) → CashpointReceiver → PurchaseAPI
```

`CashpointStub` lives in `external-inbound-kafka`. Like the supplier stubs it is not part of
the hexagonal architecture — it simulates a point-of-sale system that emits a Kafka message when a
customer pays at checkout. It calls `GET /api/products` via a MicroProfile REST Client each time the
scheduler fires to discover which products are in stock, then picks 2–4 of them at random.

`CashpointReceiver` in `inbound-kafka` is the actual inbound adapter: it receives the Kafka
message and calls `PurchaseAPI`, exactly as a Kafka delivery receiver calls `InventoryAPI`.

`PurchaseAPI` is also reachable directly via the REST endpoint (`/api/products/purchase`), which
bypasses Kafka entirely and is what tests and tooling use to drive a purchase synchronously.

The `/shop` HTML page reaches the same port for a different reason: it is not a simulation at all,
but the actual online-shopping channel — a customer who is not physically in the store, buying
through a browser instead of a till. See the next section for how `PurchaseAPI` serves both of these
callers, plus the physical cashpoint, without knowing which one it is.

---

## One inbound port, multiple adapters

`PurchaseAPI` is a good example of how a single inbound port can be driven by more than one adapter
without any change to the core:

| Adapter | Technology | Use case |
|---|---|---|
| `CashpointReceiver` | Kafka (`cashpoint-purchases`) | Physical cashpoint: payment terminal emits an event when a customer pays in-store |
| `ShopReceiver` | HTML form (`POST /shop/checkout`) | Online shop: a customer submits a basket through the browser |
| `ProductApiReceiver` | REST (`POST /api/products/purchase`) | Any script or external frontend submitting a purchase directly |

All three adapters translate a different external event into the same `PurchaseAPI.purchase()` call.
The core is unaware of how the purchase arrived. Adding a fourth channel — say a mobile push
notification or a voice assistant — would mean adding one more adapter class with zero changes to
`PurchaseHandler` or `PurchaseAPI`.

This is the central promise of hexagonal architecture: the application core defines *what* can happen;
adapters decide *how* it is triggered.

---

## External stub modules: technology-per-module, product-type-per-package

The four `external-*` modules simulate external systems that would be separate
services in production. They are **not part of the hexagonal architecture** of
the application — they sit entirely outside its boundary.

**Module cut — by direction and technology.** Modules are named
`external-outbound-<tech>` for systems the application calls outbound
(suppliers), and `external-inbound-<tech>` for systems that drive the
application inbound (cashpoint). Within each direction, one module per
technology keeps the total module count low.

| Module | Technology | Stubs inside |
|---|---|---|
| `external-outbound-rest` | JAX-RS endpoints + Kafka producer | Fruit, Vegetable, Dairy suppliers |
| `external-outbound-soap` | CXF SOAP endpoints + Kafka producer | Beverage, Meat, Bakery suppliers |
| `external-outbound-kafka` | Kafka consumer + producer | Non-food supplier |
| `external-inbound-kafka` | Quarkus Scheduler + Kafka producer | Customer checkout (cashpoint) |

**Package structure — by product type, no sharing.** Within each module,
every product type lives in its own sub-package
(`external.rest.fruit`, `external.rest.vegetable`, `external.rest.dairy`, …).
Types such as `DeliveryMessage` and `OrderRequest` that have the same shape
are deliberately duplicated rather than shared. The reason: in reality each
supplier is a completely independent system that knows nothing about the
others. Sharing a class would falsely imply a common contract between systems
that have no relationship. A future change to one supplier's message format
must not affect any other supplier — isolation at the package level enforces
that.

**Package root.** The package root is `com.example.hexademo.external.*`,
not `…adapter.external.*`. The word "adapter" is reserved for modules that
implement or consume a hexagonal port; these stubs do neither.

---

## HTML interface vs. JSON API

There are two HTML pages, each aimed at a different kind of user, sharing the
same core ports:

- **`/admin`** — supermarket staff: inventory view, supplier ordering forms
  grouped by technology, and an audit log view backed by `AuditLogAPI`.
- **`/shop`** — customers: a cart-style purchase form over `PurchaseAPI`, plus
  a dev-only "Randomize" button (pure client-side JavaScript, no server round
  trip) that fills in random quantities so testers don't have to type values
  by hand.

Both pages are thin `Templates`-based Qute receivers in `inbound-rest`; neither
contains business logic, they only translate form submissions into calls on
core API interfaces.

The JSON API at `/api/products` serves a different purpose: it is aimed at
tests and development tooling. During development of backend functionality —
new handlers, new adapters, new Kafka flows — it is faster to drive the system
through HTTP calls in a unit or integration test than to open a browser. The
flow tests in `app-server` use this API exclusively for that reason.

The JSON API also provides a natural integration point if someone wants to
build a separate frontend using Angular, React, or another framework. That
frontend would be a completely separate project consuming the API; it is not
part of this system and would not live in this repository.

---

## What this POC does not show

- **Authentication / authorization** — out of scope.
- **Schema evolution / Avro** — Kafka messages use plain JSON for simplicity.
- **Production deployment** — no Kubernetes manifests; Dev Services handles
  infrastructure for dev and test.
- **Native build** — no `quarkus.package.type=native` configuration.

These are all compatible with the architecture but would add noise without
illuminating the hexagonal structure.
