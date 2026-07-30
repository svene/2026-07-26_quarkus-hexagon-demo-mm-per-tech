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
`adapter-fruits`, `adapter-beverages`, `adapter-vegetables` module, each
containing everything related to that product type: the REST client, the Kafka
receiver, the database entity. This is intuitive at first. But as the number of
product categories grows (7 here, hundreds in a real system), the number of
modules explodes — and every module does the same thing, just for a different
noun.

**Chosen approach: one module per technology.** `adapter-outbound-httpclient`
handles *all* REST-client suppliers (fruits, vegetables, dairy). Adding a new
REST-based supplier means adding one class to an existing module and wiring one
Kafka channel. No new module, no new pom.xml, no new Maven dependency.

The benefits:

- **Coherence**: all code for "how to call a REST API" lives together. If the
  REST client configuration changes, there is exactly one place to change it.
- **Bounded blast radius**: a CXF upgrade affects only
  `adapter-outbound-webservice`. Everything else is untouched.
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

The three `adapter-external-*-stub` modules simulate supplier systems that in
production would be separate services. They run inside the same Quarkus process
in dev and test — made possible by Quarkus Dev Services and the fact that all
Kafka topics are shared inside the same Redpanda container.

**Deliberate decoupling:** stubs share no Java types with the adapters that call
them. The contract is the wire protocol:

- REST stubs: same HTTP path and JSON structure.
- SOAP stubs: same WSDL `targetNamespace` and operation name. The SEI interface
  is duplicated — one copy in `adapter-outbound-webservice`, one in
  `adapter-external-beverage-supplier-stub`. That is intentional: in production
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

## Scheduler as an inbound adapter

`adapter-inbound-scheduler` contains a single Quarkus `@Scheduled` bean. From
the hexagonal perspective a timer tick is just another way to drive the
application — it is an inbound port. The scheduler translates a time event into
a call on `PurchaseAPI`, exactly as a Kafka receiver translates a message into a
call on `InventoryAPI`.

---

## What this POC does not show

- **Authentication / authorization** — out of scope.
- **Schema evolution / Avro** — Kafka messages use plain JSON for simplicity.
- **Production deployment** — no Kubernetes manifests; Dev Services handles
  infrastructure for dev and test.
- **Native build** — no `quarkus.package.type=native` configuration.

These are all compatible with the architecture but would add noise without
illuminating the hexagonal structure.
