# System Participants by Maven Module

Complete inventory of all classes participating in the system flows, organized by Maven module.

**Source**: Derived from architecture-flow.md with module mappings from actual code structure.
**Maintenance**: see `../ai/maintaining-module-participants.md`.

**Package scheme (as of 2026-09-13)**: every module — `core` included — is organized as `org.svenehrke.triptychdemo.feature.<commodity>` (fruit, vegetable, dairy, beverage, meat, bakery, nonfood) or `org.svenehrke.triptychdemo.cross(.<concern>)` for cross-cutting concerns (inventory, auditlog, products, purchase, cashpoint, and the admin/shop/json-api aggregator receivers). There is no `core.application`/`core.api`/`core.spi`/`adapter.inbound.*`/`adapter.outbound.*` package scheme anymore — `core`'s previous `APIs.java`/`SPIs.java` container classes were split into standalone top-level interfaces, one per port, each moved into its feature or cross package. On 2026-09-27 the inbound `*API` interfaces were removed altogether - receivers inject the `*Handler` classes directly (see `concepts.md`, "Why inbound ports have no interface"). `external-*` modules are untouched by this and keep their own `org.svenehrke.triptychdemo.external.*` root (they are not part of the hexagonal architecture — see `concepts.md`).

## Quick Reference: All Modules & Participants

| Module | Participants |
|--------|--------------|
| **inbound-http-html** | `AdminReceiver`<br>`ShopReceiver`<br>`ShopCart` |
| **inbound-http-jsonapi** | `ProductApiReceiver`<br>`XxxOrderRequest` (+ `OrderRequest`)/`PurchaseRequest`/`PurchaseRequestItem`/`RequestStructureErrorMessages`<br>`JsonInputErrors`/`StrictJsonReader`/`JsonResponses` |
| **inbound-kafka** | `FruitDeliveryReceiver`<br>`VegetablesDeliveryReceiver`<br>`DairyDeliveryReceiver`<br>`BeveragesDeliveryReceiver`<br>`MeatDeliveryReceiver`<br>`BakeryDeliveryReceiver`<br>`NonFoodDeliveryReceiver`<br>`CashpointReceiver` |
| **core** | `FruitSupplierSPI`/`FruitDelivery`/`FruitsHandler`<br>`VegetablesSupplierSPI`/`VegetableDelivery`/`VegetablesHandler`<br>`DairySupplierSPI`/`DairyDelivery`/`DairyHandler`<br>`BeverageSupplierSPI`/`BeverageDelivery`/`BeveragesHandler`<br>`MeatSupplierSPI`/`MeatDelivery`/`MeatHandler`<br>`BakerySupplierSPI`/`BakeryDelivery`/`BakeryHandler`<br>`NonFoodSupplierSPI`/`NonFoodDelivery`/`NonFoodHandler`<br>`InventoryRepositorySPI`/`InventoryHandler`/`InventoryChangesHandler`<br>`AuditLogSPI`/`AuditLogHandler`/`AuditLogEntry`<br>`ProductsHandler`/`Product`/`ProductType`<br>`PurchaseHandler`/`PurchaseItem` |
| **outbound-postgres** | `InventoryService`<br>`ProductEntity` |
| **outbound-mongodb** | `AuditLogService`<br>`AuditLogEntryEntity` |
| **outbound-httpclient** | `FruitSupplierService`<br>`VegetablesSupplierService`<br>`DairySupplierService`<br>`FruitSupplierClient`<br>`VegetablesSupplierClient`<br>`DairySupplierClient` |
| **outbound-webservice** | `BeverageSupplierService`<br>`MeatSupplierService`<br>`BakerySupplierService`<br>`BeverageOrderService`<br>`MeatOrderService`<br>`BakeryOrderService` |
| **outbound-kafka** | `NonFoodSupplierService` |
| **external-outbound-rest** | `FruitSupplierStub`<br>`VegetablesSupplierStub`<br>`DairySupplierStub` |
| **external-outbound-soap** | `BeverageSupplierStub`<br>`MeatSupplierStub`<br>`BakerySupplierStub` |
| **external-outbound-kafka** | `NonFoodSupplierStub` |
| **external-inbound-kafka** | `CashpointStub`<br>`ProductsApiClient` |

---

## inbound-http-html

**Purpose**: HTTP inbound adapter for HTML form-based user interfaces
**Package**: `org.svenehrke.triptychdemo.cross` (both receivers are cross-cutting aggregators — Admin touches every commodity's ordering Handler, Shop touches Products+Purchase — so neither lives in a `feature.<name>` package)

### Receivers
- `AdminReceiver` - Admin dashboard and ordering endpoints (GET /admin shell, GET /admin/page and fragments, POST /admin/order-*)
- `ShopReceiver` - Customer shopping interface (GET /shop shell, GET /shop/page and fragment, GET /shop/events SSE stream, POST /shop/checkout)
- `UiRoute`, `UiResponse`, `*VM` records - the `{route, vm}` JSON envelope and the view models (TS types generated from them)
- `*.ts` next to the receivers - hono/html templates + the `hono` htmx extension, bundled into `hx-hono.js`
- `ShopCart` (package-private) - the checkout form's cart: pairs names with quantities, drops blank/`0` rows, parses to a `ParsedPurchase`, maps violations back to product names

**Responsibilities**:
- Parse HTTP form requests (APPLICATION_FORM_URLENCODED)
- Route to the appropriate core Handlers
- Return a static page shell, then `{route, vm}` JSON that the browser renders with hono/html templates
- Manage session state for browser interactions

**Technology**: Quarkus REST (JAX-RS) + Jackson; hono/html templates bundled by esbuild (frontend-maven-plugin), VM types via typescript-generator, checked by tsc

---

## inbound-http-jsonapi

**Purpose**: HTTP inbound adapter for JSON REST API
**Package**: `org.svenehrke.triptychdemo.cross` (spans every commodity's ordering Handler plus Products/Purchase, so it's cross-cutting like inbound-http-html)

### Receivers
- `ProductApiReceiver` - REST API endpoints (GET /api/products, POST /api/products/order-*, POST /api/products/purchase)

### Request Models (one record per file)
- `FruitOrderRequest` - Fruit order (productName, quantity)
- `VegetableOrderRequest` - Vegetable order (productName, quantity)
- `DairyOrderRequest` - Dairy order (productName, quantity)
- `BeverageOrderRequest` - Beverage order (productName, quantity)
- `MeatOrderRequest` - Meat order (productName, quantity)
- `BakeryOrderRequest` - Bakery order (productName, quantity)
- `NonFoodOrderRequest` - Non-food order (productName, quantity)
- `PurchaseRequest` - Purchase request (List of PurchaseRequestItem)
- `PurchaseRequestItem` - Purchase item (productName, quantity)
- `RequestStructureErrorMessages` - shared structure-error messages
- `OrderRequest` (package-private interface) - common shape of the seven `XxxOrderRequest`s, used to log the raw request
- Each request record has a static `structureErrors(request)`: structure checks only (missing body/field/entry, all errors collected); `PurchaseRequest` delegates each item (incl. `null` entries) to `PurchaseRequestItem.structureErrors(item, "items[i]")`, which prefixes its messages with that path; values are validated by the domain `parse()` in the receiver

### JSON input handling
- `StrictJsonReader` - `@CustomDeserialization` reader without silent scalar coercions (global mapper untouched)
- `JsonInputErrors` - messages for Jackson deserialization errors, used by `ProductApiReceiver`'s resource-local `@ServerExceptionMapper`s
- `JsonResponses` - `badRequest(List<String>)`, the `400` JSON array

**Responsibilities**:
- Parse HTTP JSON requests (APPLICATION_JSON)
- Deserialize JSON into request objects
- Route to the appropriate core Handlers
- Return JSON responses
- Validate API input contracts

**Technology**: Quarkus REST (JAX-RS), Jackson (JSON serialization)

---

## inbound-kafka

**Purpose**: Kafka inbound adapters that consume events from Kafka topics
**Package**: `org.svenehrke.triptychdemo.feature.<commodity>` for the seven delivery receivers (one commodity per package); `org.svenehrke.triptychdemo.cross.cashpoint` for the checkout event (cashpoint is a cross-cutting concern, not a commodity)

### Delivery Receivers (by product category)
- `FruitDeliveryReceiver` (`feature.fruit`) - Consumes from `fruit-deliveries` topic
- `VegetablesDeliveryReceiver` (`feature.vegetable`) - Consumes from `vegetables-deliveries` topic
- `DairyDeliveryReceiver` (`feature.dairy`) - Consumes from `dairy-deliveries` topic
- `BeveragesDeliveryReceiver` (`feature.beverage`) - Consumes from `beverages-deliveries` topic
- `MeatDeliveryReceiver` (`feature.meat`) - Consumes from `meat-deliveries` topic
- `BakeryDeliveryReceiver` (`feature.bakery`) - Consumes from `bakery-deliveries` topic
- `NonFoodDeliveryReceiver` (`feature.nonfood`) - Consumes from `nonfood-deliveries` topic

### Other Event Receivers
- `CashpointReceiver` (`cross.cashpoint`) - Consumes from `cashpoint-purchases` topic (customer purchases from external checkout); also in this package: `PurchaseMessage`, `PurchaseMessageItem`, `PurchaseMessageDeserializer`

**Responsibilities**:
- Listen to incoming Kafka messages via @Incoming annotation
- Deserialize messages
- Route to inventory/purchase handlers for processing
- Handle delivery/event notifications asynchronously

---

## core

**Purpose**: Domain + application layer - entities, use cases, and all inbound/outbound ports. Deliberately free of any infrastructure dependency (no JDBC, no Kafka client, no HTTP client).
**Package root**: `org.svenehrke.triptychdemo` - organized as `feature.<commodity>` (one package per commodity, holding that commodity's SPI, domain record and Handler together) or `cross.<concern>` (inventory, auditlog, products, purchase - each holding its SPI/Handler/domain type together). `APIs.java`/`SPIs.java` (previously one file each, holding every port as a nested interface) no longer exist - each SPI is now its own standalone top-level interface, filed directly into its feature or cross package (the former inbound `*API` interfaces were removed on 2026-09-27).

### feature.fruit
- `FruitSupplierSPI` - Interface for fruit supplier (method: placeOrder)
- `FruitDelivery` - Delivery domain record (productName, quantity)
- `FruitsHandler` - Handles fruit orders (method: order; injects FruitSupplierSPI + AuditLogSPI)

### feature.vegetable
- `VegetablesSupplierSPI`, `VegetableDelivery`, `VegetablesHandler` - same shape as feature.fruit

### feature.dairy
- `DairySupplierSPI`, `DairyDelivery`, `DairyHandler` - same shape as feature.fruit

### feature.beverage
- `BeverageSupplierSPI`, `BeverageDelivery`, `BeveragesHandler` - same shape as feature.fruit

### feature.meat
- `MeatSupplierSPI`, `MeatDelivery`, `MeatHandler` - same shape as feature.fruit

### feature.bakery
- `BakerySupplierSPI`, `BakeryDelivery`, `BakeryHandler` - same shape as feature.fruit

### feature.nonfood
- `NonFoodSupplierSPI`, `NonFoodDelivery`, `NonFoodHandler` - same shape as feature.fruit

### cross.inventory
- `InventoryRepositorySPI` - Interface for inventory data access (methods: findAll, addAmount, deductAll)
- `OnShortage` - Enum passed to deductAll: `REJECT` (online, deduct nothing) | `CAP_AT_ZERO` (physical store)
- `StockDeduction` - Result of deductAll (updated products, shortages)
- `Shortage` - Domain record (productName, requested, available) with rejection and discrepancy messages
- `InventoryHandler` - Updates inventory from delivery events, for all commodities (methods: updateFruitAmount, updateVegetableAmount, updateDairyAmount, updateBeverageAmount, updateMeatAmount, updateBakeryAmount, updateNonFoodAmount) - imports each commodity's `*Delivery` record from its `feature.<commodity>` package
- `InventoryChangesHandler` - Tells inbound adapters the inventory changed (methods: publishChange - called by InventoryHandler and PurchaseHandler after a committed change; changes - a JDK `Flow.Publisher` that ShopReceiver's SSE stream subscribes to). Not an SPI: notifications flow core → inbound adapter, which reaches core only through Handlers. In-process only.

### cross.auditlog
- `AuditLogSPI` - Interface for audit log persistence (methods: log, findRecent)
- `AuditLogHandler` - Retrieves audit log entries (method: recent)
- `AuditLogEntry` - Domain record (event, details, timestamp) - not to be confused with `outbound-mongodb`'s `AuditLogEntryEntity` (the Panache persistence entity); the two used to share the name `AuditLogEntry` until 2026-09-13, when the entity was renamed to avoid a fully-qualified-name collision once both landed in `cross.auditlog`

### cross.products
- `ProductsHandler` - Lists all products (method: listAll; injects InventoryRepositorySPI)
- `Product` - Domain record (name, type, availableAmount)
- `ProductType` - Enum (FRUIT, VEGETABLE, DAIRY, BEVERAGE, MEAT, BAKERY, NON_FOOD)

### cross.purchase
- `PurchaseOutcome` - Sealed result of checkout (`Completed` | `Rejected`)
- `PurchaseHandler` - Handles customer purchases (methods: checkout - online, rejects on insufficient stock; recordStoreSale - physical store, never rejects; injects InventoryRepositorySPI + AuditLogSPI)
- `PurchaseItem` - Domain record (productName, quantity)

**Responsibilities**:
- Implement business logic for each use case
- Coordinate between inbound adapters (Receivers) and outbound ports (SPIs)
- Log events to audit trail
- Invoke supplier services for orders
- Manage inventory updates

**Design Pattern**: Each Handler's public methods are its inbound port - there is no separate API interface; receivers inject the Handler class directly. A Handler uses one or more SPIs (outbound ports). Handlers depend on the SPI interface, not the concrete implementation - implementations are injected at runtime from the relevant `outbound-*` module.

---

## outbound-postgres

**Purpose**: PostgreSQL persistence adapter - implements InventoryRepositorySPI
**Package**: `org.svenehrke.triptychdemo.cross.inventory`

### Services
- `InventoryService` - Implements InventoryRepositorySPI using Hibernate/Panache ORM
  - Manages ProductEntity persistence
  - Handles inventory additions and deductions
  - Queries all products with type filtering
- `ProductEntity` - Panache entity backing the `products` table

**Technology**: Quarkus Panache (ORM), Hibernate, PostgreSQL
**Database**: `products` table in PostgreSQL
**Transactional**: Yes (@Transactional on write operations)

---

## outbound-mongodb

**Purpose**: MongoDB persistence adapter - implements AuditLogSPI
**Package**: `org.svenehrke.triptychdemo.cross.auditlog`

### Services
- `AuditLogService` - Implements AuditLogSPI using Panache MongoDB
- `AuditLogEntryEntity` - Panache Mongo entity backing the `audit_log` collection (renamed from `AuditLogEntry` on 2026-09-13 to avoid colliding with core's domain record of that name once both moved into `cross.auditlog`)

**Responsibilities**:
  - Persist audit log entries with timestamps
  - Query recent entries sorted by timestamp descending
  - Pagination support (limit parameter)

**Technology**: Quarkus Panache MongoDB, MongoDB
**Database**: `audit_log` collection in MongoDB
**Sorting**: By timestamp descending (newest first)

---

## outbound-httpclient

**Purpose**: REST HTTP client adapter - implements REST-based Supplier SPIs
**Package**: `org.svenehrke.triptychdemo.feature.<commodity>` (fruit, dairy, vegetable)

### Services (by product category)
- `FruitSupplierService` - Implements FruitSupplierSPI using REST client
- `VegetablesSupplierService` - Implements VegetablesSupplierSPI using REST client
- `DairySupplierService` - Implements DairySupplierSPI using REST client

### REST Clients (auto-generated from service interfaces)
- `FruitSupplierClient` - REST client proxy for fruit supplier
- `VegetablesSupplierClient` - REST client proxy for vegetable supplier
- `DairySupplierClient` - REST client proxy for dairy supplier

**Technology**: Quarkus REST Client (MicroProfile), HTTP/REST
**Configuration**: Endpoints configured in application.properties
**In Dev/Test**: Points to in-process stubs (same Quarkus instance)

---

## outbound-webservice

**Purpose**: SOAP web service adapter - implements SOAP-based Supplier SPIs
**Package**: `org.svenehrke.triptychdemo.feature.<commodity>` (beverage, meat, bakery)

### Services (by product category)
- `BeverageSupplierService` - Implements BeverageSupplierSPI using SOAP client
- `MeatSupplierService` - Implements MeatSupplierSPI using SOAP client
- `BakerySupplierService` - Implements BakerySupplierSPI using SOAP client

### SOAP Clients (auto-generated from WSDL)
- `BeverageOrderService` - SOAP service interface for beverage orders
- `MeatOrderService` - SOAP service interface for meat orders
- `BakeryOrderService` - SOAP service interface for bakery orders

**Technology**: Apache CXF (SOAP/WS), WSDL
**Endpoint Path**: `/soap/[beverage|meat|bakery]-supplier`
**Configuration**: CXF client configurations in application.properties
**In Dev/Test**: Points to in-process stubs (same Quarkus instance)

---

## outbound-kafka

**Purpose**: Kafka producer adapter - implements Kafka-based Supplier SPI
**Package**: `org.svenehrke.triptychdemo.feature.nonfood` (named even though it's the only feature in this module, so the `TriptychArchitecture` slices rule (checked by `ArchitectureTest` in `app-server`) stays fully automatic - see `concepts.md`)

### Services
- `NonFoodSupplierService` - Implements NonFoodSupplierSPI using Kafka emitter
  - Publishes order messages to `nonfood-orders` topic
  - Uses SmallRye Reactive Messaging Emitter

**Technology**: Quarkus SmallRye Reactive Messaging, Kafka Emitter
**Topic**: `nonfood-orders` (configured in application.properties as `nonfood-orders-out` channel)
**Pattern**: One-way async messaging (fire-and-forget)

---

## external-outbound-rest

**Purpose**: Mock REST supplier stubs - simulates external REST APIs
**Package**: `org.svenehrke.triptychdemo.external.outbound.rest` (unchanged - external-* modules are not part of the hexagonal architecture, see `concepts.md`, and were not touched by the 2026-09-13 feature/cross restructuring)

### Supplier Stubs
- `FruitSupplierStub` - Mock REST endpoint for fruit supplier
- `VegetablesSupplierStub` - Mock REST endpoint for vegetable supplier
- `DairySupplierStub` - Mock REST endpoint for dairy supplier

**Responsibilities**:
- Receive order requests via REST (called by outbound-httpclient services)
- Publish delivery notifications to Kafka topics (`fruit-deliveries`, `vegetables-deliveries`, `dairy-deliveries`)
- Simulate supplier behavior

**Technology**: JAX-RS REST endpoint, Quarkus SmallRye Reactive Messaging Emitter
**In Dev/Test**: Runs in same Quarkus instance as main application
**Integration**: Completes the REST → Kafka cycle for product deliveries

---

## external-outbound-soap

**Purpose**: Mock SOAP supplier stubs - simulates external SOAP web services
**Package**: `org.svenehrke.triptychdemo.external.outbound.soap` (unchanged, see note above)

### Supplier Stubs
- `BeverageSupplierStub` - Mock SOAP endpoint for beverage supplier
- `MeatSupplierStub` - Mock SOAP endpoint for meat supplier
- `BakerySupplierStub` - Mock SOAP endpoint for bakery supplier

**Responsibilities**:
- Receive order requests via SOAP (called by outbound-webservice services)
- Publish delivery notifications to Kafka topics (`beverages-deliveries`, `meat-deliveries`, `bakery-deliveries`)
- Simulate supplier behavior

**Technology**: Apache CXF SOAP endpoint, Quarkus SmallRye Reactive Messaging Emitter
**In Dev/Test**: Runs in same Quarkus instance as main application
**WSDL Path**: `/soap/[beverage|meat|bakery]-supplier`
**Integration**: Completes the SOAP → Kafka cycle for product deliveries

---

## external-outbound-kafka

**Purpose**: Mock Kafka supplier stub - simulates external Kafka-based order processor
**Package**: `org.svenehrke.triptychdemo.external.outbound.kafka.nonfood` (unchanged, see note above)

### Supplier Stubs
- `NonFoodSupplierStub` - Mock Kafka consumer/producer for non-food supplier
  - Consumes from `nonfood-orders` topic
  - Publishes to `nonfood-deliveries` topic

**Responsibilities**:
- Listen to order events from `nonfood-orders` topic
- Process orders asynchronously
- Publish delivery notifications to `nonfood-deliveries` topic
- Simulate supplier behavior

**Technology**: Quarkus SmallRye Reactive Messaging (@Incoming, @Outgoing)
**Topics**:
  - Inbound: `nonfood-orders` (consumes orders)
  - Outbound: `nonfood-deliveries` (publishes deliveries)
**Pattern**: Two-topic Kafka cycle (order request → delivery response)
**Integration**: Completes the Kafka → Kafka cycle for non-food products

---

## external-inbound-kafka

**Purpose**: External event sources - simulates external systems sending events into the system
**Package**: `org.svenehrke.triptychdemo.external.inbound.kafka` (unchanged, see note above)

### Mock Clients
- `CashpointStub` - Mock checkout system that generates purchase events
- `ProductsApiClient` - Mock external system that queries products

**Responsibilities**:
- Simulate external systems publishing events
- In test scenarios: trigger purchase events via `cashpoint-purchases` topic
- In demo: can be used to simulate real-world purchase patterns

**Technology**: Kafka producer (for test scenarios), REST client (for queries)

---

## Summary by Layer

### Presentation Layer (HTTP Inbound)
- `inbound-http-html` module - HTML user interfaces (AdminReceiver, ShopReceiver)
- `inbound-http-jsonapi` module - JSON REST API (ProductApiReceiver)
- Responsibility: Handle HTTP requests, return HTTP responses (HTML or JSON)
- Package: `cross` in both modules (both aggregate across every commodity)

### Asynchronous Event Layer (Kafka Inbound)
- `inbound-kafka` module
- Participants: FruitDeliveryReceiver, VegetablesDeliveryReceiver, DairyDeliveryReceiver, BeveragesDeliveryReceiver, MeatDeliveryReceiver, BakeryDeliveryReceiver, NonFoodDeliveryReceiver (each in its own `feature.<commodity>` package), CashpointReceiver (in `cross.cashpoint`)
- Responsibility: Consume Kafka events, trigger business logic

### Core Business Layer
- `core` module, organized as `feature.<commodity>` (7 packages) + `cross.<concern>` (4 packages: inventory, auditlog, products, purchase)
- Participants: Handlers (12 total), SPI interfaces (9 total), domain records/enum (10 total)
- Responsibility: Implement business logic, coordinate flow between inbound and outbound

### Data Persistence Layer
- `outbound-postgres` module - InventoryService (ProductEntity), package `cross.inventory`
- `outbound-mongodb` module - AuditLogService (AuditLogEntryEntity), package `cross.auditlog`
- Responsibility: Persist and query data

### Supplier Integration Layer (Outbound Adapters)
- `outbound-httpclient` - REST suppliers (Fruits, Vegetables, Dairy), each in its own `feature.<commodity>` package
- `outbound-webservice` - SOAP suppliers (Beverages, Meat, Bakery), each in its own `feature.<commodity>` package
- `outbound-kafka` - Kafka supplier (NonFood), package `feature.nonfood`
- Responsibility: Call external supplier systems

### Mock External Systems Layer
- `external-outbound-rest` - Mock REST suppliers
- `external-outbound-soap` - Mock SOAP suppliers
- `external-outbound-kafka` - Mock Kafka supplier
- `external-inbound-kafka` - Mock event sources
- Responsibility: Simulate external system behavior via Kafka integration
- Package root: `org.svenehrke.triptychdemo.external.*` for all four - untouched by the feature/cross restructuring, since these modules sit outside the hexagonal architecture entirely (see `concepts.md`)

---

## Participant Count by Module

| Module | Participants | Type |
|--------|--------------|------|
| inbound-http-html | 2 | HTTP HTML Receivers |
| inbound-http-jsonapi | 2 | HTTP JSON API Receiver + request records |
| inbound-kafka | 8 + 3 | Kafka Receivers + cashpoint message types |
| core | 12 Handlers, 9 SPI interfaces, 10 domain records/enum | Feature (7 packages) + Cross (4 packages) |
| outbound-postgres | 2 | Service (InventoryService) + Entity (ProductEntity) |
| outbound-mongodb | 2 | Service (AuditLogService) + Entity (AuditLogEntryEntity) |
| outbound-httpclient | 3 | Services + 3 REST Clients |
| outbound-webservice | 3 | Services + 3 SOAP Clients |
| outbound-kafka | 1 | Service (NonFoodSupplierService) |
| external-outbound-rest | 3 | Supplier Stubs |
| external-outbound-soap | 3 | Supplier Stubs |
| external-outbound-kafka | 1 | Supplier Stub |
| external-inbound-kafka | 2 | Mock Event Sources |
| **Total** | **~65 classes** | **across 15 modules** |

---

**For instructions on adding a new commodity, adding a new adapter technology, or otherwise keeping this file in sync with code changes**, see `../ai/maintaining-module-participants.md`.
