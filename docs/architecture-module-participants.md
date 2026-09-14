# System Participants by Maven Module

Complete inventory of all classes participating in the system flows, organized by Maven module.

**Source**: Derived from architecture-flow.md with module mappings from actual code structure.
**Maintenance**: When new classes are added or refactored, update both this file and architecture-flow.md.

**Package scheme (as of 2026-09-13)**: every module — `core` included — is organized as `org.svenehrke.triptychdemo.feature.<commodity>` (fruit, vegetable, dairy, beverage, meat, bakery, nonfood) or `org.svenehrke.triptychdemo.cross(.<concern>)` for cross-cutting concerns (inventory, auditlog, products, purchase, cashpoint, and the admin/shop/json-api aggregator receivers). There is no `core.application`/`core.api`/`core.spi`/`adapter.inbound.*`/`adapter.outbound.*` package scheme anymore — `core`'s previous `APIs.java`/`SPIs.java` container classes were split into standalone top-level interfaces, one per port, each moved into its feature or cross package. `external-*` modules are untouched by this and keep their own `org.svenehrke.triptychdemo.external.*` root (they are not part of the hexagonal architecture — see `concepts.md`).

## Quick Reference: All Modules & Participants

| Module | Participants |
|--------|--------------|
| **inbound-http-html** | `AdminReceiver`<br>`ShopReceiver` |
| **inbound-http-jsonapi** | `ProductApiReceiver`<br>`Requests` |
| **inbound-kafka** | `FruitDeliveryReceiver`<br>`VegetablesDeliveryReceiver`<br>`DairyDeliveryReceiver`<br>`BeveragesDeliveryReceiver`<br>`MeatDeliveryReceiver`<br>`BakeryDeliveryReceiver`<br>`NonFoodDeliveryReceiver`<br>`CashpointReceiver` |
| **core** | `FruitsAPI`/`FruitSupplierSPI`/`FruitDelivery`/`FruitsHandler`<br>`VegetablesAPI`/`VegetablesSupplierSPI`/`VegetableDelivery`/`VegetablesHandler`<br>`DairyAPI`/`DairySupplierSPI`/`DairyDelivery`/`DairyHandler`<br>`BeveragesAPI`/`BeverageSupplierSPI`/`BeverageDelivery`/`BeveragesHandler`<br>`MeatAPI`/`MeatSupplierSPI`/`MeatDelivery`/`MeatHandler`<br>`BakeryAPI`/`BakerySupplierSPI`/`BakeryDelivery`/`BakeryHandler`<br>`NonFoodAPI`/`NonFoodSupplierSPI`/`NonFoodDelivery`/`NonFoodHandler`<br>`InventoryAPI`/`InventoryRepositorySPI`/`InventoryHandler`<br>`AuditLogAPI`/`AuditLogSPI`/`AuditLogHandler`/`AuditLogEntry`<br>`ProductsAPI`/`ProductsHandler`/`Product`/`ProductType`<br>`PurchaseAPI`/`PurchaseHandler`/`PurchaseItem` |
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
**Package**: `org.svenehrke.triptychdemo.cross` (both receivers are cross-cutting aggregators — Admin touches every commodity's ordering API, Shop touches Products+Purchase — so neither lives in a `feature.<name>` package)

### Receivers
- `AdminReceiver` - Admin dashboard and ordering endpoints (GET /admin, POST /admin/order-*)
- `ShopReceiver` - Customer shopping interface (GET /shop, POST /shop/checkout)

**Responsibilities**:
- Parse HTTP form requests (APPLICATION_FORM_URLENCODED)
- Route to appropriate core API handlers
- Return HTML responses via Qute templates
- Manage session state for browser interactions

**Technology**: Quarkus REST (JAX-RS), Qute templating engine

---

## inbound-http-jsonapi

**Purpose**: HTTP inbound adapter for JSON REST API
**Package**: `org.svenehrke.triptychdemo.cross` (spans every commodity's ordering API plus Products/Purchase, so it's cross-cutting like inbound-http-html)

### Receivers
- `ProductApiReceiver` - REST API endpoints (GET /api/products, POST /api/products/order-*, POST /api/products/purchase)

### Request Models (consolidated in Requests interface)
- `Requests.FruitOrderRequest` - Fruit order (productName, quantity)
- `Requests.VegetableOrderRequest` - Vegetable order (productName, quantity)
- `Requests.DairyOrderRequest` - Dairy order (productName, quantity)
- `Requests.BeverageOrderRequest` - Beverage order (productName, quantity)
- `Requests.MeatOrderRequest` - Meat order (productName, quantity)
- `Requests.BakeryOrderRequest` - Bakery order (productName, quantity)
- `Requests.NonFoodOrderRequest` - Non-food order (productName, quantity)
- `Requests.PurchaseRequest` - Purchase request (List of PurchaseRequestItem)
- `Requests.PurchaseRequestItem` - Purchase item (productName, quantity)

**Responsibilities**:
- Parse HTTP JSON requests (APPLICATION_JSON)
- Deserialize JSON into request objects
- Route to appropriate core API handlers
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
**Package root**: `org.svenehrke.triptychdemo` - organized as `feature.<commodity>` (one package per commodity, holding that commodity's API, SPI, domain record and Handler together) or `cross.<concern>` (inventory, auditlog, products, purchase - each holding its API/SPI/Handler/domain type together). `APIs.java`/`SPIs.java` (previously one file each, holding every port as a nested interface) no longer exist - each port is now its own standalone top-level interface, filed directly into its feature or cross package.

### feature.fruit
- `FruitsAPI` - Order fruits (method: order)
- `FruitSupplierSPI` - Interface for fruit supplier (method: placeOrder)
- `FruitDelivery` - Delivery domain record (productName, quantity)
- `FruitsHandler` - Handles fruit orders (implements FruitsAPI, injects FruitSupplierSPI + AuditLogSPI)

### feature.vegetable
- `VegetablesAPI`, `VegetablesSupplierSPI`, `VegetableDelivery`, `VegetablesHandler` - same shape as feature.fruit

### feature.dairy
- `DairyAPI`, `DairySupplierSPI`, `DairyDelivery`, `DairyHandler` - same shape as feature.fruit

### feature.beverage
- `BeveragesAPI`, `BeverageSupplierSPI`, `BeverageDelivery`, `BeveragesHandler` - same shape as feature.fruit

### feature.meat
- `MeatAPI`, `MeatSupplierSPI`, `MeatDelivery`, `MeatHandler` - same shape as feature.fruit

### feature.bakery
- `BakeryAPI`, `BakerySupplierSPI`, `BakeryDelivery`, `BakeryHandler` - same shape as feature.fruit

### feature.nonfood
- `NonFoodAPI`, `NonFoodSupplierSPI`, `NonFoodDelivery`, `NonFoodHandler` - same shape as feature.fruit

### cross.inventory
- `InventoryAPI` - Update inventory (methods: updateFruitAmount, updateVegetableAmount, updateDairyAmount, updateBeverageAmount, updateMeatAmount, updateBakeryAmount, updateNonFoodAmount) - imports each commodity's `*Delivery` record from its `feature.<commodity>` package
- `InventoryRepositorySPI` - Interface for inventory data access (methods: findAll, addAmount, deductAmount)
- `InventoryHandler` - Updates inventory from delivery events (implements InventoryAPI for all commodities)

### cross.auditlog
- `AuditLogAPI` - Retrieve audit history (method: recent)
- `AuditLogSPI` - Interface for audit log persistence (methods: log, findRecent)
- `AuditLogHandler` - Retrieves audit log entries (implements AuditLogAPI)
- `AuditLogEntry` - Domain record (event, details, timestamp) - not to be confused with `outbound-mongodb`'s `AuditLogEntryEntity` (the Panache persistence entity); the two used to share the name `AuditLogEntry` until 2026-09-13, when the entity was renamed to avoid a fully-qualified-name collision once both landed in `cross.auditlog`

### cross.products
- `ProductsAPI` - Query all products (method: listAll)
- `ProductsHandler` - Lists all products (implements ProductsAPI, injects InventoryRepositorySPI)
- `Product` - Domain record (name, type, availableAmount)
- `ProductType` - Enum (FRUIT, VEGETABLE, DAIRY, BEVERAGE, MEAT, BAKERY, NON_FOOD)

### cross.purchase
- `PurchaseAPI` - Process customer purchases (method: purchase)
- `PurchaseHandler` - Handles customer purchases (implements PurchaseAPI, injects InventoryRepositorySPI + AuditLogSPI)
- `PurchaseItem` - Domain record (productName, quantity)

**Responsibilities**:
- Implement business logic for each use case
- Coordinate between inbound ports (APIs) and outbound ports (SPIs)
- Log events to audit trail
- Invoke supplier services for orders
- Manage inventory updates

**Design Pattern**: Each handler implements one API (inbound port) and uses one or more SPIs (outbound ports). Each API is implemented by exactly one Handler in the same feature/cross package. Handlers depend on the SPI interface, not the concrete implementation - implementations are injected at runtime from the relevant `outbound-*` module.

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
- Participants: Handlers (11 total), API interfaces (11 total), SPI interfaces (9 total), domain records/enum (10 total)
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
| inbound-http-jsonapi | 2 | HTTP JSON API Receiver + Requests |
| inbound-kafka | 8 + 3 | Kafka Receivers + cashpoint message types |
| core | 11 Handlers, 11 API interfaces, 9 SPI interfaces, 10 domain records/enum | Feature (7 packages) + Cross (4 packages) |
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

## Maintenance Guide

**When adding a new commodity:**

1. Create a new `feature.<name>` package in `core` with `<Name>API`, `<Name>SupplierSPI`, `<Name>Delivery`, `<Name>Handler` as standalone top-level types (no more `APIs.java`/`SPIs.java` containers to extend)
2. Add the corresponding case to `InventoryAPI`/`InventoryHandler` in `cross.inventory` and to `ProductType` in `cross.products`
3. Create a `feature.<name>` package in the appropriate outbound module based on integration type:
   - REST: `outbound-httpclient/feature/<name>/<Name>SupplierService` + REST client
   - SOAP: `outbound-webservice/feature/<name>/<Name>SupplierService` + SOAP client
   - Kafka: `outbound-kafka/feature/<name>/<Name>SupplierService`
4. Create a mock supplier stub (package unchanged, `external.outbound.<tech>`):
   - REST: `external-outbound-rest/<Name>Stub`
   - SOAP: `external-outbound-soap/<Name>Stub`
   - Kafka: `external-outbound-kafka/<Name>Stub`
5. Create a `feature.<name>` package in `inbound-kafka` with `<Name>DeliveryReceiver` if needed
6. No test change needed for the cross-feature-isolation check: `ArchitectureTest` (in `app-server`) delegates to the reusable `TriptychArchitecture` ArchRule (`app-server/src/test/java/.../devsupport/`), whose slices rule covers any `feature.<name>` package automatically once it exists
7. Update this file and architecture-flow.md

**When refactoring class names:**
- Update all references in this file (module → participants)
- Update architecture-flow.md
- Update sequence diagrams in flows/ directory
- Update any memory/reference files
- Watch for fully-qualified-name collisions across modules when moving a class into `cross` or a shared `feature.<name>` package - two different classes with the same simple name in the same package, in different module jars, will silently shadow each other at runtime with no compile error (see the `AuditLogEntry`/`AuditLogEntryEntity` case above). `maven-enforcer-plugin`'s `banDuplicateClasses` rule (bound to `verify` in `app-server/pom.xml`) catches this at build time.
