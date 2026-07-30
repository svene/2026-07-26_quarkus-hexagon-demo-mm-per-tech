# quarkus-hexagon-demo-mm-per-tech

A working supermarket inventory system that demonstrates hexagonal architecture
with Quarkus.  One Maven module per adapter technology, a single combined
domain+application core, and one deployable (`app-server`) that wires it all
together.

---

## Usage

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker or Podman running (Quarkus Dev Services starts containers automatically)

### Start the application

```bash
mvn -pl app-server quarkus:dev
```

Quarkus Dev Services starts real containers for PostgreSQL, MongoDB, and Kafka
(Redpanda) automatically. No docker-compose, no manual connection strings.

Open **http://localhost:8080/products** in your browser.

### What you can do in the browser

The page shows the current inventory table and a set of order forms grouped by
the underlying technology of the outbound adapter:

| Section | Products you can order | Adapter technology |
|---|---|---|
| REST suppliers | Fruits, Vegetables, Dairy | HTTP REST client |
| SOAP suppliers | Beverages, Meat, Bakery | SOAP / Apache CXF |
| Kafka supplier | Non-food | Kafka producer |

1. **Order a product** — fill in a name and quantity and click *Order*. The order
   goes to a supplier (stub running in the same process). The supplier sends a
   Kafka delivery event. The Kafka receiver updates inventory. Refresh the page
   and the new stock appears.

2. **Simulate a customer purchase** — fill in up to three product names with
   quantities and click *Purchase*. The amounts are deducted immediately. If a
   product does not exist the line is silently ignored.

3. **Automatic purchases** — once inventory is non-empty, a Quarkus Scheduler
   fires every 2 seconds (30 s initial delay) and randomly purchases 2–4
   products at once. Refresh the page to see the inventory shrink over time.

### JSON API

A JSON API is also available at `/api/products` for scripting or testing:

```
GET  /api/products                          → list all products
POST /api/products/order-{fruits|vegetables|dairy|beverages|meat|bakery|nonfood}
POST /api/products/purchase
```

Order POST bodies are JSON: `{"productName": "Mango", "quantity": 5}`.
Purchase POST body: `{"items":[{"productName":"Mango","quantity":5}]}`.

---

## Developer guide

### Repository layout

```
core/                       Domain model, use-case interfaces (API), SPI
inbound-rest/               JAX-RS — HTML UI + JSON API
inbound-kafka/              Kafka @Incoming — delivery events + purchase events
outbound-postgres/          Hibernate ORM / Panache — inventory persistence
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
| `inbound-rest` | Inbound adapter | JAX-RS + Qute templates |
| `inbound-kafka` | Inbound adapter | SmallRye Reactive Messaging |
| `outbound-postgres` | Outbound adapter | Hibernate ORM / Panache |
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

1. Add a `ProductType` constant to `core/.../domain/ProductType.java`.
2. Add an API interface in `core/.../port/in/` (e.g. `SnacksAPI`).
3. Add an SPI interface in `core/.../port/out/` for the chosen supplier technology.
4. Implement a Handler in `core/.../application/` that calls the SPI.
5. Implement the outbound adapter in the matching `outbound-*` module.
6. Add a delivery receiver in `inbound-kafka`.
7. Add an external stub (or extend an existing one).
8. Wire Kafka channel names and REST/SOAP client keys in `app-server/application.properties`.
9. Add a form to `inbound-rest/templates/ProductReceiver/products.html`.

### Adding a new adapter technology

1. Create a new Maven module `{direction}-{technology}`.
2. Add the module to the root `pom.xml` `<modules>` list and
   `<dependencyManagement>`.
3. Declare the dependency in `app-server/pom.xml`.
4. Add an SPI interface in `core/.../port/out/` (or reuse an existing one).
5. Implement the SPI in the new module.
