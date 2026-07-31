# Architecture Flow - Kafka Integration Reference

Technical reference for understanding the Kafka-based integration patterns and topic configurations in the supermarket inventory system.

**For human-readable flow diagrams showing all primary flows**, see `architecture-flow.md`.

## Maintenance Notes

**When code or Maven modules change**, update both architecture flow files:

1. **architecture-flow.md** (human-readable flows)
   - Update HTTP endpoint descriptions if receiver methods change
   - Add/remove endpoints if handlers are added/removed
   - Update handler/API names if they're refactored
   - Do NOT recreate from scratch—just update the affected sections

2. **architecture-flow-kafka-reference.md** (this file - technical reference)
   - Update Kafka topic configurations if `application.properties` changes
   - Add/remove topic cycles if new suppliers are added
   - Update producer/consumer class names if they're refactored
   - Cross-check with `application.properties` and module source code
   - Do NOT recreate from scratch—just update the affected sections

**Key files to check when maintaining these documents:**
- `app-server/src/main/resources/application.properties` - Kafka topic configuration
- `**/src/main/java/**/adapter/inbound/**Receiver.java` - HTTP/Kafka entry points
- `**/src/main/java/**/core/application/**Handler.java` - Business logic
- `**/src/main/java/**/adapter/outbound/**Service.java` - Outbound integrations

---

## Data Persistence

### PostgreSQL (outbound-postgres)
- **InventoryService**: Manages product inventory
  - `addAmount()`: Called by delivery receivers to add stock
  - `deductAmount()`: Called by purchase handlers to reduce stock
  - `findAll()`: Called by product list endpoints
  - Storage: ProductEntity table

### MongoDB (outbound-mongodb)
- **AuditLogService**: Logs all system events
  - `log()`: Called by handlers to record operations
  - `findRecent()`: Called by audit-log endpoints
  - Storage: AuditLogEntry collection

## External System Integrations (Outbound)

### REST API Clients (outbound-httpclient)
- **FruitSupplierService** → FruitSupplierClient
- **VegetablesSupplierService** → VegetablesSupplierClient
- **DairySupplierService** → DairySupplierClient

All endpoint: `placeOrder(productName, quantity)`

### SOAP Web Services (outbound-webservice)
- **BeverageSupplierService** → BeverageOrderService (SOAP)
- **MeatSupplierService** → MeatOrderService (SOAP)
- **BakerySupplierService** → BakeryOrderService (SOAP)

All endpoint: `placeOrder(productName, quantity)`

### Kafka Producer (outbound-kafka)
- **NonFoodSupplierService** → Emitter (nonfood-orders-out channel)
  - Publishes: NonFoodOrderMessage to `nonfood-orders` topic

## Kafka Topic Cycles: Request-Response Through Events

These cycles show how external supplier integrations (REST/SOAP stubs) are decoupled using Kafka topics:

### REST Supplier Cycles (Fruits, Vegetables, Dairy)

**Topic: fruit-deliveries**
- **Producer**: FruitSupplierStub (in external-outbound-rest)
- **Consumer**: FruitDeliveryReceiver (in inbound-kafka)
- **Trigger**: Admin POST /admin/order-fruits → FruitSupplierService → REST call → Stub publishes delivery
- **Config**: 
  - Outgoing: `mp.messaging.outgoing.fruit-deliveries-out.topic=fruit-deliveries`
  - Incoming: `mp.messaging.incoming.fruit-deliveries.topic=fruit-deliveries`

**Topic: vegetables-deliveries**
- **Producer**: VegetablesSupplierStub (in external-outbound-rest)
- **Consumer**: VegetablesDeliveryReceiver (in inbound-kafka)
- **Trigger**: Admin POST /admin/order-vegetables
- **Config**: 
  - Outgoing: `mp.messaging.outgoing.vegetables-deliveries-out.topic=vegetables-deliveries`
  - Incoming: `mp.messaging.incoming.vegetables-deliveries.topic=vegetables-deliveries`

**Topic: dairy-deliveries**
- **Producer**: DairySupplierStub (in external-outbound-rest)
- **Consumer**: DairyDeliveryReceiver (in inbound-kafka)
- **Trigger**: Admin POST /admin/order-dairy
- **Config**: 
  - Outgoing: `mp.messaging.outgoing.dairy-deliveries-out.topic=dairy-deliveries`
  - Incoming: `mp.messaging.incoming.dairy-deliveries.topic=dairy-deliveries`

### SOAP Supplier Cycles (Beverages, Meat, Bakery)

**Topic: beverages-deliveries**
- **Producer**: BeverageSupplierStub (in external-outbound-soap)
- **Consumer**: BeveragesDeliveryReceiver (in inbound-kafka)
- **Trigger**: Admin POST /admin/order-beverages → BeverageSupplierService → SOAP call → Stub publishes delivery
- **Config**: 
  - Outgoing: `mp.messaging.outgoing.beverages-deliveries-out.topic=beverages-deliveries`
  - Incoming: `mp.messaging.incoming.beverages-deliveries.topic=beverages-deliveries`

**Topic: meat-deliveries**
- **Producer**: MeatSupplierStub (in external-outbound-soap)
- **Consumer**: MeatDeliveryReceiver (in inbound-kafka)
- **Trigger**: Admin POST /admin/order-meat
- **Config**: 
  - Outgoing: `mp.messaging.outgoing.meat-deliveries-out.topic=meat-deliveries`
  - Incoming: `mp.messaging.incoming.meat-deliveries.topic=meat-deliveries`

**Topic: bakery-deliveries**
- **Producer**: BakerySupplierStub (in external-outbound-soap)
- **Consumer**: BakeryDeliveryReceiver (in inbound-kafka)
- **Trigger**: Admin POST /admin/order-bakery
- **Config**: 
  - Outgoing: `mp.messaging.outgoing.bakery-deliveries-out.topic=bakery-deliveries`
  - Incoming: `mp.messaging.incoming.bakery-deliveries.topic=bakery-deliveries`

### Two-Topic Kafka Supplier Cycle (NonFood)

**Topic 1: nonfood-orders (Order Request)**
- **Producer**: NonFoodSupplierService (in outbound-kafka)
- **Consumer**: NonFoodSupplierStub (in external-outbound-kafka)
- **Trigger**: Admin POST /admin/order-nonfood → NonFoodSupplierService emits order
- **Config**: 
  - Outgoing: `mp.messaging.outgoing.nonfood-orders-out.topic=nonfood-orders`
  - Incoming (stub): `mp.messaging.incoming.nonfood-orders.topic=nonfood-orders`

**Topic 2: nonfood-deliveries (Delivery Response)**
- **Producer**: NonFoodSupplierStub (in external-outbound-kafka)
- **Consumer**: NonFoodDeliveryReceiver (in inbound-kafka)
- **Trigger**: NonFoodSupplierStub receives order from Topic 1 → publishes delivery to Topic 2
- **Config**: 
  - Outgoing (stub): `mp.messaging.outgoing.nonfood-deliveries-out.topic=nonfood-deliveries`
  - Incoming: `mp.messaging.incoming.nonfood-deliveries.topic=nonfood-deliveries`

### Cashpoint Purchase Cycle

**Topic: cashpoint-purchases**
- **Producer**: External checkout systems (simulated by CashpointStub)
- **Consumer**: CashpointReceiver (in inbound-kafka)
- **Flow**: Cashpoint event → PurchaseHandler → Inventory deduction
- **Config**: 
  - Incoming: `mp.messaging.incoming.cashpoint-purchases.topic=cashpoint-purchases`
  - Outgoing (for testing): `mp.messaging.outgoing.cashpoint-purchases-out.topic=cashpoint-purchases`

## Summary of All Endpoints (Including Indirect Kafka Flows)

| Receiver | Route | Method | Flow Type | Kafka Topic Connection | Data Sinks |
|----------|-------|--------|-----------|------------------------|-----------|
| AdminReceiver | /admin | GET | Query | - | PostgreSQL (read) |
| AdminReceiver | /admin/inventory-fragment | GET | Query | - | PostgreSQL (read) |
| AdminReceiver | /admin/audit-fragment | GET | Query | - | MongoDB (read) |
| AdminReceiver | /admin/order-fruits | POST | Command | **→ fruit-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-vegetables | POST | Command | **→ vegetables-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-dairy | POST | Command | **→ dairy-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-beverages | POST | Command | **→ beverages-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-meat | POST | Command | **→ meat-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-bakery | POST | Command | **→ bakery-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-nonfood | POST | Command | **→ nonfood-orders** → **← nonfood-deliveries** | PostgreSQL + MongoDB |
| ShopReceiver | /shop | GET | Query | - | PostgreSQL (read) |
| ShopReceiver | /shop/inventory-fragment | GET | Query | - | PostgreSQL (read) |
| ShopReceiver | /shop/checkout | POST | Command | - | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products | GET | Query | - | PostgreSQL (read) |
| ProductApiReceiver | /api/products/order-fruits | POST | Command | **→ fruit-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-vegetables | POST | Command | **→ vegetables-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-dairy | POST | Command | **→ dairy-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-beverages | POST | Command | **→ beverages-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-meat | POST | Command | **→ meat-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-bakery | POST | Command | **→ bakery-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-nonfood | POST | Command | **→ nonfood-orders** → **← nonfood-deliveries** | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/purchase | POST | Command | - | PostgreSQL + MongoDB |
| FruitDeliveryReceiver | **← fruit-deliveries** | Event | Delivery | Consumes: **fruit-deliveries** | PostgreSQL + MongoDB |
| VegetablesDeliveryReceiver | **← vegetables-deliveries** | Event | Delivery | Consumes: **vegetables-deliveries** | PostgreSQL + MongoDB |
| DairyDeliveryReceiver | **← dairy-deliveries** | Event | Delivery | Consumes: **dairy-deliveries** | PostgreSQL + MongoDB |
| BeveragesDeliveryReceiver | **← beverages-deliveries** | Event | Delivery | Consumes: **beverages-deliveries** | PostgreSQL + MongoDB |
| MeatDeliveryReceiver | **← meat-deliveries** | Event | Delivery | Consumes: **meat-deliveries** | PostgreSQL + MongoDB |
| BakeryDeliveryReceiver | **← bakery-deliveries** | Event | Delivery | Consumes: **bakery-deliveries** | PostgreSQL + MongoDB |
| NonFoodDeliveryReceiver | **← nonfood-deliveries** | Event | Delivery | Consumes: **nonfood-deliveries** | PostgreSQL + MongoDB |
| CashpointReceiver | **← cashpoint-purchases** | Event | Purchase | Consumes: **cashpoint-purchases** | PostgreSQL + MongoDB |
