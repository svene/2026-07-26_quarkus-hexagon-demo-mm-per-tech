# quarkus-hexagon-demo-mm-per-tech

A working supermarket inventory system that demonstrates hexagonal architecture
with Quarkus.  One Maven module per adapter technology, a single combined
domain+application core, and one deployable (`app-server`) that wires it all
together.

**[Concepts →](concepts.md)** Read about the business domain (supermarket inventory system), hexagonal architecture patterns (inbound ports/adapters, outbound ports/adapters), and the technologies used.

---

## Usage

### Prerequisites

- Java 25 (pinned in `.sdkmanrc`: `sdk env` switches to it; Quarkus 3.33 cannot build Java 27 bytecode)
- Maven 3.9+
- Docker or Podman running (Quarkus Dev Services starts containers automatically)

### Start the application

```bash
mvn -pl app-server quarkus:dev
```

Quarkus Dev Services starts real containers for PostgreSQL, MongoDB, and Kafka
(Redpanda) automatically. No docker-compose, no manual connection strings.

Open **http://localhost:8080/admin** in your browser.

### What you can do in the browser

There are two HTML pages, aimed at two different kinds of user, plus a JSON API:

- **`/admin`** — supermarket staff: inventory view, supplier ordering forms
- **`/audit-log`** — the latest 300 audit log entries, reloaded with a *Refresh* button
- **`/shop`** — customers: browse in-stock products and buy a basket of items
- **`/api/products`** — JSON API for scripts, tests, and other frontends

The admin page shows the current inventory table and a set of order forms
grouped by the underlying technology of the outbound adapter:

| Section | Products you can order | Adapter technology |
|---|---|---|
| REST suppliers | Fruits, Vegetables, Dairy | HTTP REST client |
| SOAP suppliers | Beverages, Meat, Bakery | SOAP / Apache CXF |
| Kafka supplier | Non-food | Kafka producer |

1. **Order a product** (on `/admin`) — fill in a name and quantity and click
   *Order*. The order goes to a supplier (stub running in the same process).
   The supplier sends a Kafka delivery event. The Kafka receiver updates
   inventory. No need to refresh — both `/admin` and `/shop` update their
   inventory as soon as it changes (server-sent event, `GET /inventory/events`).

   The DC doesn't start empty: after the start (at the first period close,
   1 min in) and after *Reset demo data*, the DC orders 500 of every listed
   product it doesn't carry yet from the suppliers (`inventory.dc-seed.*`), and
   the stores pull from it, so the demo runs on its own.

2. **Purchase a basket of products** (on `/shop`) — fill in quantities for one
   or more in-stock products and click *Purchase*. The amounts are deducted
   immediately. See the Shop UI section below for details, including the
   dev-only randomize button.

3. **Simulated customer checkouts** — a background `CashpointStub` stands in for
   the customers of the three stores: starting 30 s after the app is ready,
   customers enter each store, shop and pay at one of its tills (Zurich 4,
   Basel 2, Bern 1; 15 s per customer), each paying
   customer checking out 2–4 random in-stock products, deducted from inventory
   exactly as a real point-of-sale terminal would. Watch the numbers on
   `/admin` or `/locations` shrink on their own, no refresh needed. On
   `/locations` each store's *Purchases* table lists its latest sales (time,
   products, units) next to the *Requests* to the DC they trigger; the online
   FC's lists the shop's checkouts.

4. **Store occupancy and tills** (on `/locations`) — each store's line shows
   how many customers are inside (of its capacity), how many queue for a till,
   how many tills are busy and how many customers paid and were turned away in
   the last demo minute, as the checkout systems report it every 5 s (Kafka topic
   `store-occupancy`). At the rush hour the queue grows until the store is
   full, and new customers turn away - lost sales. The app opens a till when
   the queue gets longer than the open tills (or the store is full and every
   till busy) and closes one when two stand idle (tag *auto*; switch:
   `inventory.auto-tills.enabled`): more of them get in, fewer turned away,
   more sales (and the store's stock falls faster). Each change is in the
   audit log (`TILLS_OPENED` / `TILLS_CLOSED`). Below the line, two charts of
   the last 10 minutes show the trend: customers inside vs. capacity (time
   spent full), and queue, paid and turned away per minute with the open
   tills - so you can see whether a till raises the throughput.

### JSON API

A JSON API is also available at `/api/products` for scripting or testing:

```
GET  /api/products                          → list all products
POST /api/products/order-{fruits|vegetables|dairy|beverages|meat|bakery|nonfood}
POST /api/products/purchase
```

Order POST bodies are JSON: `{"productName": "Mango", "quantity": 5}`.
Purchase POST body: `{"items":[{"productName":"Mango","quantity":5}]}`.

### Admin UI

The admin page at **http://localhost:8080/admin** covers everything above:
the supplier order forms on the left and the inventory table on the right. The
audit log (event, details, timestamp, read from MongoDB) has its own page at
**http://localhost:8080/audit-log**: the latest 300 entries, no live updates,
reloaded with its *Refresh* button. The inventory table updates as soon as the
inventory changes (same server-sent event as `/shop`), and each row has a
*Restock* quantity and button that orders more of that product from its
supplier. So
multiple browser tabs (or `/shop` running alongside) stay in sync without a
manual reload.
The 7 order forms are grouped into REST / SOAP / Kafka supplier tabs. Each
offers its products and the quantities 10/50/100/500 as radio buttons, plus a
custom quantity field; its *Order* button stays disabled until both are chosen.

### Shop UI

A customer-facing shopping page is available at **http://localhost:8080/shop**.
It lists every in-stock product with a quantity field per row; filling in one
or more quantities and clicking *Purchase* submits the whole basket in a single
call to `PurchaseHandler.checkout(...)`. The list (sorted by name) updates as
soon as the inventory changes: the server pushes an `inventoryChanged`
server-sent event (`GET /inventory/events`), and the page re-fetches its products
and morphs them in, so new products appear and sold-out ones disappear without
touching the quantities you're typing. A *Randomize (dev)*
button fills 2–4 random rows with random quantities (client-side JavaScript
only, no server round trip) so you don't have to type values by hand while
developing; it never submits, you still click *Purchase*.

### Styling and live updates

Both `/admin` and `/shop` use [Bulma](https://bulma.io) for styling and
[htmx](https://htmx.org) (4.0.0, plus its `hx-sse` extension on `/shop`) for
the polling and push updates described above. Both libraries are served
locally — no CDN — from
`inbound-http-html/src/main/resources/META-INF/resources/{css,js}`, which Quarkus
serves automatically at the web root (`/css/bulma.min.css`,
`/js/htmx.org/4.0.0/htmx.js`).

---

## Developer guide

### Repository layout

```
core/                       Domain model, use cases (Handlers), SPI
inbound-http-html/          JAX-RS + hono/html (rendered in the browser) — HTML UI (/admin, /shop, /locations)
inbound-http-jsonapi/       JAX-RS — JSON API (/api/products, /api/locations)
inbound-kafka/              Kafka @Incoming — delivery events + purchase events
inbound-event/              CDI @ObservesAsync — domain events fired by core (delivery reached the DC)
outbound-postgres/          plain SQL over JDBC (Agroal) — inventory persistence
outbound-mongodb/           MongoDB / Panache — audit log
outbound-httpclient/        MicroProfile REST Client — REST supplier orders
outbound-webservice/        Apache CXF client — SOAP supplier orders
outbound-kafka/             Kafka @Channel Emitter — non-food supplier orders
external-outbound-rest/     JAX-RS endpoints that echo delivery events onto Kafka
external-outbound-soap/     CXF SOAP endpoints that echo delivery events onto Kafka
external-outbound-kafka/    Kafka consumer/producer stub for non-food
external-inbound-kafka/     Quarkus Scheduler + Kafka producer — cashpoint stub
app-server/                 Deployable: wires everything, holds application.properties
```

**Dependency rules:** `core` depends on nothing in this tree. Every adapter
depends only on `core`. Stubs share only the wire contract with adapters (HTTP
path, WSDL, Kafka topic). `app-server` depends on `core` + all adapters + all
stubs.

### Module map

| Module | Role | Technology |
|---|---|---|
| `core` | Domain + application (use cases + ports) | plain Java + CDI |
| `inbound-http-html` | Inbound adapter | JAX-RS + hono/html templates rendered in the browser (esbuild via frontend-maven-plugin) |
| `inbound-http-jsonapi` | Inbound adapter | JAX-RS (JSON) |
| `inbound-kafka` | Inbound adapter | SmallRye Reactive Messaging |
| `inbound-event` | Inbound adapter | CDI async events (`@ObservesAsync`) |
| `outbound-postgres` | Outbound adapter | Plain SQL over JDBC (Agroal), Flyway |
| `outbound-mongodb` | Outbound adapter | MongoDB / Panache |
| `outbound-httpclient` | Outbound adapter | MicroProfile REST Client |
| `outbound-webservice` | Outbound adapter | Apache CXF (SOAP client) |
| `outbound-kafka` | Outbound adapter | SmallRye Reactive Messaging |
| `external-outbound-rest` | External system stub | JAX-RS + Kafka producer |
| `external-outbound-soap` | External system stub | CXF SOAP server + Kafka producer |
| `external-outbound-kafka` | External system stub | Kafka consumer + producer |
| `external-inbound-kafka` | External system stub | Quarkus Scheduler + Kafka producer |
| `app-server` | Deployable | Quarkus runner, no business logic |

### Running tests

```bash
mvn test -pl app-server -am
```

Quarkus Dev Services starts the containers for the test run. The integration
tests use Awaitility to wait for Kafka messages to travel through the pipeline.

While working on a change, run only what it touches, and the full suite once
before committing (times measured 2026-10-06):

| Scope | Command | Time |
|---|---|---|
| Domain logic only | `mvn test -pl core` | ≈ 2 s |
| Single test classes | `mvn test -pl app-server -am -Dtest='ShopReceiverTest,Cashpoint*' -Dsurefire.failIfNoSpecifiedTests=false` | ≈ 25 s |
| Quick suite | `mvn test -pl app-server -am -DexcludedGroups=slow` | ≈ 65 s |
| Full suite | `mvn test -pl app-server -am` | ≈ 1:35 |

The quick suite skips the classes tagged `slow` (`KafkaMalformedMessageTest`,
`KafkaTransientFailureTest`): each needs a Quarkus instance of its own. Run them
when you touch Kafka channel configuration or a Kafka receiver's error handling.

Which tests cover what (all in `app-server` unless noted):

| Change in | Tests |
|---|---|
| Domain records, parsing, validation (`core`) | `core` unit tests, e.g. `FruitDeliveryTest`, `PurchaseTest`, `DemandEstimateTest` |
| HTML pages (`inbound-http-html`) | `AdminReceiverTest`, `ShopReceiverTest`, `AuditLogReceiverTest`, `LandingPageTest`, `StaticResourcesTest`; `ShopCartTest` in `inbound-http-html`; the Playwright tests |
| JSON API (`inbound-http-jsonapi`) | `ProductApiReceiverTest` |
| Ordering, deliveries, Kafka topics | `*OrderDeliveryFlowTest`, `SupplierOrderFlowTest`, `SupplierLeadTimeFlowTest` |
| Stores, shipments, replenishment | `ReplenishmentFlowTest`, `AutoReplenishmentFlowTest`, `ShipmentFlowTest`, `ShipmentTransitFlowTest`, `DcSeedFlowTest`, `AutoPurchasingFlowTest` |
| Cashpoints, store occupancy | `CashpointFlowTest`, `CashpointViaKafkaFlowTest`, `StoreOccupancyFlowTest`; `StoreSimulationTest` in `external-inbound-kafka`, `StoreMetricsVMTest` in `inbound-http-html` |
| Live updates (SSE) | `InventoryEventsReceiverTest` |
| Kafka error handling (DLQ, retry) | `KafkaMalformedMessageTest`, `KafkaTransientFailureTest` |
| Package moves, new modules | `ArchitectureTest` |

Interactive alternative: `mvn -pl app-server quarkus:test` (Quarkus continuous
testing) reruns only the tests affected by each saved change.

### Running the Playwright end-to-end tests

```bash
cd e2e-playwright
npm ci
npx playwright install
npm test
```

The test suite starts `mvn quarkus:dev` in the background, waits for the server
to be ready, then runs the browser tests. Requires Docker/Podman for Dev Services.

### Adding a new product category

Core (and every adapter module) is organized by `feature.<commodity>` package,
not by port/application/domain layer — see [concepts.md](concepts.md) and
`docs/ai/maintaining-module-participants.md` for the full picture. In short:

1. Create a new `feature.<name>` package in `core` with
   `<Name>SupplierSPI`, `<Name>Delivery`, `<Name>Handler` as standalone
   top-level types.
2. Add the corresponding case to `InventoryHandler` in
   `core/.../cross/inventory/` and to `ProductType` in
   `core/.../cross/products/`.
3. Implement the outbound adapter in the matching `outbound-*` module's new
   `feature.<name>` package.
4. Add a delivery receiver in `inbound-kafka`'s `feature.<name>` package, if
   needed.
5. Add an external stub (or extend an existing one).
6. Wire Kafka channel names and REST/SOAP client keys in
   `app-server/application.properties`.
7. Add a form entry to `SUPPLIER_BOXES` in
   `inbound-http-html/src/main/java/org/svenehrke/triptychdemo/cross/admin.ts`.

### Adding a new adapter technology

1. Create a new Maven module `{direction}-{technology}`.
2. Add the module to the root `pom.xml` `<modules>` list and
   `<dependencyManagement>`.
3. Declare the dependency in `app-server/pom.xml`.
4. Reuse the existing SPI interface for the commodity (in `core/.../feature/<name>/`
   or `core/.../cross/<concern>/`) rather than declaring a new one.
5. Implement the SPI in the new module.
