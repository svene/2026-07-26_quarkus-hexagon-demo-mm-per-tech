# System Participants by Maven Module

Complete inventory of all classes participating in the system flows, organized by Maven module.

**Source**: Derived from architecture-flow.md with module mappings from actual code structure.
**Maintenance**: When new classes are added or refactored, update both this file and architecture-flow.md.

## Quick Reference: All Modules & Participants

| Module | Participants |
|--------|--------------|
| **inbound-http** | `AdminReceiver`<br>`ShopReceiver`<br>`ProductApiReceiver` |
| **inbound-kafka** | `FruitDeliveryReceiver`<br>`VegetablesDeliveryReceiver`<br>`DairyDeliveryReceiver`<br>`BeveragesDeliveryReceiver`<br>`MeatDeliveryReceiver`<br>`BakeryDeliveryReceiver`<br>`NonFoodDeliveryReceiver`<br>`CashpointReceiver` |
| **core (application)** | `ProductsHandler`<br>`InventoryHandler`<br>`FruitsHandler`<br>`VegetablesHandler`<br>`DairyHandler`<br>`BeveragesHandler`<br>`MeatHandler`<br>`BakeryHandler`<br>`NonFoodHandler`<br>`PurchaseHandler`<br>`AuditLogHandler` |
| **core (api)** | `ProductsAPI`<br>`InventoryAPI`<br>`FruitsAPI`<br>`VegetablesAPI`<br>`DairyAPI`<br>`BeveragesAPI`<br>`MeatAPI`<br>`BakeryAPI`<br>`NonFoodAPI`<br>`PurchaseAPI`<br>`AuditLogAPI` |
| **core (spi)** | `InventoryRepositorySPI`<br>`AuditLogSPI`<br>`FruitSupplierSPI`<br>`VegetablesSupplierSPI`<br>`DairySupplierSPI`<br>`BeverageSupplierSPI`<br>`MeatSupplierSPI`<br>`BakerySupplierSPI`<br>`NonFoodSupplierSPI` |
| **outbound-postgres** | `InventoryService` |
| **outbound-mongodb** | `AuditLogService` |
| **outbound-httpclient** | `FruitSupplierService`<br>`VegetablesSupplierService`<br>`DairySupplierService`<br>`FruitSupplierClient`<br>`VegetablesSupplierClient`<br>`DairySupplierClient` |
| **outbound-webservice** | `BeverageSupplierService`<br>`MeatSupplierService`<br>`BakerySupplierService`<br>`BeverageOrderService`<br>`MeatOrderService`<br>`BakeryOrderService` |
| **outbound-kafka** | `NonFoodSupplierService` |
| **external-outbound-rest** | `FruitSupplierStub`<br>`VegetablesSupplierStub`<br>`DairySupplierStub` |
| **external-outbound-soap** | `BeverageSupplierStub`<br>`MeatSupplierStub`<br>`BakerySupplierStub` |
| **external-outbound-kafka** | `NonFoodSupplierStub` |
| **external-inbound-kafka** | `CashpointStub`<br>`ProductsApiClient` |

---

## inbound-http

**Purpose**: HTTP inbound adapters (JAX-RS endpoints) that receive user requests
**Package**: `com.example.hexademo.adapter.inbound.http`

### HTML Subpackage (Form-based UI)
- `AdminReceiver` - Admin dashboard and ordering endpoints (GET /admin, POST /admin/order-*)
- `ShopReceiver` - Customer shopping interface (GET /shop, POST /shop/checkout)

### JSON API Subpackage (REST API)
- `ProductApiReceiver` - REST API endpoints (GET /api/products, POST /api/products/order-*, POST /api/products/purchase)

**Responsibilities**:
- Parse HTTP requests (form data or JSON)
- Route to appropriate core API handlers
- Return responses (HTML or JSON)
- Extract query parameters and request bodies

---

## inbound-kafka

**Purpose**: Kafka inbound adapters that consume events from Kafka topics
**Package**: `com.example.hexademo.adapter.inbound.kafka`

### Delivery Receivers (by product category)
- `FruitDeliveryReceiver` - Consumes from `fruit-deliveries` topic
- `VegetablesDeliveryReceiver` - Consumes from `vegetables-deliveries` topic
- `DairyDeliveryReceiver` - Consumes from `dairy-deliveries` topic
- `BeveragesDeliveryReceiver` - Consumes from `beverages-deliveries` topic
- `MeatDeliveryReceiver` - Consumes from `meat-deliveries` topic
- `BakeryDeliveryReceiver` - Consumes from `bakery-deliveries` topic
- `NonFoodDeliveryReceiver` - Consumes from `nonfood-deliveries` topic

### Other Event Receivers
- `CashpointReceiver` - Consumes from `cashpoint-purchases` topic (customer purchases from external checkout)

**Responsibilities**:
- Listen to incoming Kafka messages via @Incoming annotation
- Deserialize messages
- Route to inventory/purchase handlers for processing
- Handle delivery/event notifications asynchronously

---

## core (application)

**Purpose**: Core business logic - use case handlers implementing the system's application services
**Package**: `com.example.hexademo.core.application`

### Product Management Handlers
- `ProductsHandler` - Lists all products (implements ProductsAPI)
- `InventoryHandler` - Updates inventory from delivery events (implements InventoryAPI for all categories)

### Category-Specific Order Handlers
- `FruitsHandler` - Handles fruit orders (implements FruitsAPI)
- `VegetablesHandler` - Handles vegetable orders (implements VegetablesAPI)
- `DairyHandler` - Handles dairy orders (implements DairyAPI)
- `BeveragesHandler` - Handles beverage orders (implements BeveragesAPI)
- `MeatHandler` - Handles meat orders (implements MeatAPI)
- `BakeryHandler` - Handles bakery orders (implements BakeryAPI)
- `NonFoodHandler` - Handles non-food orders (implements NonFoodAPI)

### Purchase & Audit Handlers
- `PurchaseHandler` - Handles customer purchases (implements PurchaseAPI)
- `AuditLogHandler` - Retrieves audit log entries (implements AuditLogAPI)

**Responsibilities**:
- Implement business logic for each use case
- Coordinate between inbound ports (APIs) and outbound ports (SPIs)
- Log events to audit trail
- Invoke supplier services for orders
- Manage inventory updates

**Design Pattern**: Each handler implements one API (inbound port) and uses one or more SPIs (outbound ports)

---

## core (api)

**Purpose**: Inbound ports (APIs) - interfaces that define the API contract for core services
**Package**: `com.example.hexademo.core.api`

### Product & Inventory APIs
- `ProductsAPI` - Query all products
- `InventoryAPI` - Update inventory (methods: updateFruitAmount, updateVegetableAmount, updateDairyAmount, updateBeverageAmount, updateMeatAmount, updateBakeryAmount, updateNonFoodAmount)

### Category-Specific APIs
- `FruitsAPI` - Order fruits (method: order)
- `VegetablesAPI` - Order vegetables (method: order)
- `DairyAPI` - Order dairy (method: order)
- `BeveragesAPI` - Order beverages (method: order)
- `MeatAPI` - Order meat (method: order)
- `BakeryAPI` - Order bakery (method: order)
- `NonFoodAPI` - Order non-food items (method: order)

### Transaction APIs
- `PurchaseAPI` - Process customer purchases (method: purchase)
- `AuditLogAPI` - Retrieve audit history (method: recent)

**Design Pattern**: Each API is implemented by exactly one Handler in core.application package

---

## core (spi)

**Purpose**: Outbound ports (SPIs) - interfaces that define contracts for external integrations
**Package**: `com.example.hexademo.core.spi`

### Data Persistence Ports
- `InventoryRepositorySPI` - Interface for inventory data access (methods: findAll, addAmount, deductAmount)
- `AuditLogSPI` - Interface for audit log persistence (methods: log, findRecent)

### Supplier Integration Ports (by product category)
- `FruitSupplierSPI` - Interface for fruit supplier (method: placeOrder)
- `VegetablesSupplierSPI` - Interface for vegetable supplier (method: placeOrder)
- `DairySupplierSPI` - Interface for dairy supplier (method: placeOrder)
- `BeverageSupplierSPI` - Interface for beverage supplier (method: placeOrder)
- `MeatSupplierSPI` - Interface for meat supplier (method: placeOrder)
- `BakerySupplierSPI` - Interface for bakery supplier (method: placeOrder)
- `NonFoodSupplierSPI` - Interface for non-food supplier (method: placeOrder)

**Design Pattern**: Each handler depends on the SPI interface, not the concrete implementation. Implementations injected at runtime.

---

## outbound-postgres

**Purpose**: PostgreSQL persistence adapter - implements InventoryRepositorySPI
**Package**: `com.example.hexademo.adapter.outbound.postgres.inventory`

### Services
- `InventoryService` - Implements InventoryRepositorySPI using Hibernate/Panache ORM
  - Manages ProductEntity persistence
  - Handles inventory additions and deductions
  - Queries all products with type filtering

**Technology**: Quarkus Panache (ORM), Hibernate, PostgreSQL
**Database**: `products` table in PostgreSQL
**Transactional**: Yes (@Transactional on write operations)

---

## outbound-mongodb

**Purpose**: MongoDB persistence adapter - implements AuditLogSPI
**Package**: `com.example.hexademo.adapter.outbound.mongodb.auditlog`

### Services
- `AuditLogService` - Implements AuditLogSPI using Panache MongoDB

**Responsibilities**:
  - Persist audit log entries with timestamps
  - Query recent entries sorted by timestamp descending
  - Pagination support (limit parameter)

**Technology**: Quarkus Panache MongoDB, MongoDB
**Database**: `AuditLogEntry` collection in MongoDB
**Sorting**: By timestamp descending (newest first)

---

## outbound-httpclient

**Purpose**: REST HTTP client adapter - implements REST-based Supplier SPIs
**Package**: `com.example.hexademo.adapter.outbound.httpclient`

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
**Package**: `com.example.hexademo.adapter.outbound.webservice`

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
**Package**: `com.example.hexademo.adapter.outbound.kafka.nonfood`

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
**Package**: `com.example.hexademo.external.outbound.rest`

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
**Package**: `com.example.hexademo.external.outbound.soap`

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
**Package**: `com.example.hexademo.external.outbound.kafka.nonfood`

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
**Package**: `com.example.hexademo.external.inbound.kafka`

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
- `inbound-http` module
- Participants: AdminReceiver, ShopReceiver, ProductApiReceiver
- Responsibility: Handle HTTP requests, return HTTP responses

### Asynchronous Event Layer (Kafka Inbound)
- `inbound-kafka` module
- Participants: FruitDeliveryReceiver, VegetablesDeliveryReceiver, DairyDeliveryReceiver, BeveragesDeliveryReceiver, MeatDeliveryReceiver, BakeryDeliveryReceiver, NonFoodDeliveryReceiver, CashpointReceiver
- Responsibility: Consume Kafka events, trigger business logic

### Core Business Layer
- `core` module (application, api, spi)
- Participants: Handlers (11 total), API interfaces (11 total), SPI interfaces (9 total)
- Responsibility: Implement business logic, coordinate flow between inbound and outbound

### Data Persistence Layer
- `outbound-postgres` module - InventoryService (ProductEntity)
- `outbound-mongodb` module - AuditLogService (AuditLogEntry)
- Responsibility: Persist and query data

### Supplier Integration Layer (Outbound Adapters)
- `outbound-httpclient` - REST suppliers (Fruits, Vegetables, Dairy)
- `outbound-webservice` - SOAP suppliers (Beverages, Meat, Bakery)
- `outbound-kafka` - Kafka suppliers (NonFood)
- Responsibility: Call external supplier systems

### Mock External Systems Layer
- `external-outbound-rest` - Mock REST suppliers
- `external-outbound-soap` - Mock SOAP suppliers
- `external-outbound-kafka` - Mock Kafka supplier
- `external-inbound-kafka` - Mock event sources
- Responsibility: Simulate external system behavior via Kafka integration

---

## Participant Count by Module

| Module | Participants | Type |
|--------|--------------|------|
| inbound-http | 3 | HTTP Receivers |
| inbound-kafka | 8 | Kafka Receivers |
| core (application) | 11 | Handlers |
| core (api) | 11 | Inbound Port Interfaces |
| core (spi) | 9 | Outbound Port Interfaces |
| outbound-postgres | 1 | Service (InventoryService) |
| outbound-mongodb | 1 | Service (AuditLogService) |
| outbound-httpclient | 3 | Services + 3 REST Clients |
| outbound-webservice | 3 | Services + 3 SOAP Clients |
| outbound-kafka | 1 | Service (NonFoodSupplierService) |
| external-outbound-rest | 3 | Supplier Stubs |
| external-outbound-soap | 3 | Supplier Stubs |
| external-outbound-kafka | 1 | Supplier Stub |
| external-inbound-kafka | 2 | Mock Event Sources |
| **Total** | **~62 classes** | **across 14 modules** |

---

## Maintenance Guide

**When adding a new supplier:**

1. Create new handler in `core/application` (e.g., `NewCategoryHandler`)
2. Create inbound API interface in `core/api` (e.g., `NewCategoryAPI`)
3. Create outbound SPI interface in `core/spi` (e.g., `NewCategorySPI`)
4. Create service adapter based on integration type:
   - REST: `outbound-httpclient/NewCategoryService` + REST client
   - SOAP: `outbound-webservice/NewCategoryService` + SOAP client
   - Kafka: `outbound-kafka/NewCategoryService`
5. Create mock supplier stub:
   - REST: `external-outbound-rest/NewCategoryStub`
   - SOAP: `external-outbound-soap/NewCategoryStub`
   - Kafka: `external-outbound-kafka/NewCategoryStub`
6. Create Kafka receiver if needed: `inbound-kafka/NewCategoryDeliveryReceiver`
7. Update this file and architecture-flow.md

**When refactoring class names:**
- Update all references in this file (module → participants)
- Update architecture-flow.md
- Update sequence diagrams in flows/ directory
- Update any memory/reference files
