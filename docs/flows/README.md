# System Flow Sequence Diagrams

PlantUML sequence diagrams for all primary flows in the supermarket inventory system. Each diagram shows the sequence of method calls, message passing, and database operations for a specific user action or event.

**Diagrams are derived from `architecture-flow.md` - keep both files synchronized when flows change.**

## Query Flows (Read-Only)

### Admin Dashboard
**File**: `admin-get-dashboard.puml`
- **Trigger**: GET /admin
- **Flow**: Browser → AdminReceiver → ProductsHandler → InventoryService → PostgreSQL
- **Returns**: Product list for dashboard display
- **Participants**: 1 (Admin)

### Admin Audit Log Fragment
**File**: `admin-get-audit.puml`
- **Trigger**: GET /admin/audit-fragment
- **Flow**: Browser → AdminReceiver → AuditLogHandler → AuditLogService → MongoDB
- **Returns**: Recent audit entries (limited to 100)
- **Participants**: 1 (Admin)

### Shop Catalog
**File**: `shop-get-catalog.puml`
- **Trigger**: GET /shop
- **Flow**: Browser → ShopReceiver → ProductsHandler → InventoryService → PostgreSQL
- **Returns**: In-stock products (availableAmount > 0) only
- **Participants**: 1 (Customer)

### API Product List
**File**: `api-get-products.puml`
- **Trigger**: GET /api/products
- **Flow**: API Client → ProductApiReceiver → ProductsHandler → InventoryService → PostgreSQL
- **Returns**: All products as JSON
- **Participants**: 1 (API Client)

## Order Flows (REST Integration)

### Admin Order Fruits
**File**: `admin-order-fruits.puml`
- **Trigger**: POST /admin/order-fruits
- **Technology**: HTML Form → REST Client → Kafka Delivery Topic
- **Flow**: 
  1. Synchronous: AdminReceiver → FruitsHandler → AuditLog + FruitSupplierService (REST call)
  2. Asynchronous: Kafka (fruit-deliveries) → FruitDeliveryReceiver → InventoryHandler → PostgreSQL
- **Participants**: Admin, REST supplier stub
- **Databases**: PostgreSQL (inventory), MongoDB (audit log), Kafka (delivery event)

### Admin Order Vegetables, Dairy
**Files**: Same pattern as fruits (REST suppliers publish to Kafka)
- `admin-order-vegetables.puml` (same structure, different product type)
- `admin-order-dairy.puml` (same structure, different product type)

## Order Flows (SOAP Integration)

### Admin Order Beverages
**File**: `admin-order-beverages.puml`
- **Trigger**: POST /admin/order-beverages
- **Technology**: HTML Form → SOAP Client → Kafka Delivery Topic
- **Flow**: 
  1. Synchronous: AdminReceiver → BeveragesHandler → AuditLog + BeverageSupplierService (SOAP call)
  2. Asynchronous: Kafka (beverages-deliveries) → BeveragesDeliveryReceiver → InventoryHandler → PostgreSQL
- **Participants**: Admin, SOAP supplier stub
- **Databases**: PostgreSQL (inventory), MongoDB (audit log), Kafka (delivery event)

### Admin Order Meat, Bakery
**Files**: Same pattern as beverages (SOAP suppliers)
- `admin-order-meat.puml` (same structure, different product type)
- `admin-order-bakery.puml` (same structure, different product type)

## Order Flows (Kafka Integration)

### Admin Order NonFood
**File**: `admin-order-nonfood.puml`
- **Trigger**: POST /admin/order-nonfood
- **Technology**: HTML Form → Kafka Order Topic → Kafka Delivery Topic (Two-Hop)
- **Flow**: 
  1. Synchronous: AdminReceiver → NonFoodHandler → AuditLog + NonFoodSupplierService (Kafka emit)
  2. Intermediate: Kafka (nonfood-orders) → NonFoodSupplierStub
  3. Asynchronous: Kafka (nonfood-deliveries) → NonFoodDeliveryReceiver → InventoryHandler → PostgreSQL
- **Participants**: Admin, Kafka supplier stub
- **Databases**: PostgreSQL (inventory), MongoDB (audit log), Kafka (2 topics)

## Purchase Flows

### Shop Checkout
**File**: `shop-checkout.puml`
- **Trigger**: POST /shop/checkout
- **Flow**: HTML Form → ShopReceiver → PurchaseHandler → InventoryService → PostgreSQL + AuditLogService → MongoDB
- **Actions**: For each item in cart: deduct from inventory, log to audit
- **Returns**: Redirect to /shop (303 See Other)
- **Participants**: 1 (Customer)
- **Databases**: PostgreSQL (inventory deduction), MongoDB (audit log)

### API Purchase
**File**: `api-purchase.puml`
- **Trigger**: POST /api/products/purchase
- **Flow**: JSON → ProductApiReceiver → PurchaseHandler → InventoryService + AuditLogService
- **Actions**: Same as shop checkout, but via JSON API
- **Returns**: OK (200) response
- **Participants**: 1 (API Client)
- **Databases**: PostgreSQL (inventory deduction), MongoDB (audit log)

## API Order Flows

### API Order Beverages
**File**: `api-order-beverages.puml`
- **Trigger**: POST /api/products/order-beverages
- **Technology**: JSON API → SOAP Client → Kafka Delivery Topic
- **Flow**: Same as admin order beverages, but triggered via JSON API
- **Participants**: API Client, SOAP supplier stub
- **Databases**: PostgreSQL (inventory), MongoDB (audit log), Kafka (delivery event)

### API Order Other Categories
**Files**: Same pattern as API order beverages
- Fruits, Vegetables, Dairy (REST suppliers)
- Meat, Bakery (SOAP suppliers)
- NonFood (Kafka two-hop)

## Event-Driven Flow (Kafka Inbound)

### Cashpoint Purchase Event
**File**: `cashpoint-purchase-event.puml`
- **Trigger**: External checkout system publishes to `cashpoint-purchases` topic
- **Technology**: Kafka Event → Kafka Inbound Receiver → Purchase Handler → PostgreSQL + MongoDB
- **Flow**: Asynchronous event processing; no response sent to external system
- **Participants**: External checkout system (producer only), CashpointReceiver (consumer)
- **Databases**: PostgreSQL (inventory deduction), MongoDB (audit log)
- **Note**: This is an indirect flow (not from primary HTTP source)

## Technology Legend

| Symbol | Meaning |
|--------|---------|
| Browser | Human user via web interface (HTML forms) |
| API Client | Machine client via JSON API endpoints |
| Receiver | HTTP inbound adapter (JAX-RS endpoints) |
| Handler | Core business logic (use cases) |
| Service | Outbound adapter (external integration) |
| Stub | Mock external system (same process in dev/test) |
| Kafka Topic | Asynchronous message queue |
| Database | PostgreSQL (inventory) or MongoDB (audit log) |

## Common Patterns

### REST Order Pattern (Fruits, Vegetables, Dairy)
```
HTML Form POST → Receiver → Handler → AuditLog + REST Service (in-process stub)
                                              ↓
                                    Kafka Topic (fruit-deliveries)
                                              ↓
                                    Kafka Receiver → InventoryHandler → PostgreSQL
```

### SOAP Order Pattern (Beverages, Meat, Bakery)
```
HTML Form POST → Receiver → Handler → AuditLog + SOAP Service (in-process stub)
                                              ↓
                                    Kafka Topic (beverages-deliveries)
                                              ↓
                                    Kafka Receiver → InventoryHandler → PostgreSQL
```

### Kafka Order Pattern (NonFood)
```
HTML Form POST → Receiver → Handler → AuditLog + Kafka Emitter
                                              ↓
                                    Kafka Topic: nonfood-orders
                                              ↓
                                    Kafka Stub (consumes orders)
                                              ↓
                                    Kafka Topic: nonfood-deliveries
                                              ↓
                                    Kafka Receiver → InventoryHandler → PostgreSQL
```

### Purchase Pattern (Shop & API)
```
Request → Receiver → PurchaseHandler → Loop: InventoryService → PostgreSQL
                                    ↓
                                  AuditLog → MongoDB
                                    ↓
                                Response (HTML or JSON)
```

## Generating Diagrams

To render these diagrams:

```bash
# Single diagram
plantuml flows/admin-order-fruits.puml -o flows/ -tpng

# All diagrams
plantuml flows/*.puml -o flows/ -tpng
```

Requires: `plantuml` CLI tool installed
Format: SVG (default) or PNG (-tpng flag)

## Maintenance

**When flows change:**
1. Update the relevant `.puml` file with new sequence
2. Update `architecture-flow.md` with corresponding text description
3. Keep both files synchronized (they document the same flows)
4. Do NOT regenerate from code—update sequences based on actual code review

**Flows to update when adding new suppliers/receivers:**
- For new product category ordered via REST: add `admin-order-[category].puml` and `api-order-[category].puml`
- For new Kafka topic: add sequence diagram showing producer/consumer cycle
- For new HTTP endpoint: add diagram showing request flow and participants
