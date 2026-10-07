# System Flow Sequence Diagrams

PlantUML sequence diagrams for all primary flows in the supermarket inventory system. Each diagram shows the sequence of method calls, message passing, and database operations for a specific user action or event.

**Diagrams are derived from `../architecture-flow.md` - keep both files synchronized when flows change.**

## Query Flows (Read-Only)

### Admin Dashboard
**File**: `admin-get-dashboard.puml`
- **Trigger**: GET /admin (static shell) → GET /admin/page
- **Flow**: Browser → AdminReceiver → ProductsHandler → InventoryService → PostgreSQL
- **Returns**: `{route: AdminPage, vm}` JSON, rendered in the browser (hono/html)
- **Participants**: 1 (Admin)

### Audit Log Page
**File**: `audit-log-page.puml`
- **Trigger**: GET /audit-log (static shell) → GET /audit-log/page, again on the *Refresh* button (no SSE, no polling)
- **Flow**: Browser → AuditLogReceiver → AuditLogHandler → AuditLogService → MongoDB
- **Returns**: `{route: AuditLogPage, vm}` JSON with the latest 300 entries, newest first
- **Participants**: 1 (Admin)

### Shop Catalog
**File**: `shop-get-catalog.puml`
- **Trigger**: GET /shop (static shell) → GET /shop/page
- **Flow**: Browser → ShopReceiver → ProductsHandler → InventoryService → PostgreSQL
- **Returns**: `{route: ShopPage, vm}` JSON with in-stock products (availableAmount > 0) only, rendered in the browser
- **Participants**: 1 (Customer)

### Inventory Change Stream
**File**: `inventory-events.puml`
- **Trigger**: shop, admin and location shells open GET /inventory/events (SSE) on load
- **Flow**: inventory change (Kafka delivery, purchase or replenishment) → core fires an `InventoryEvent` (CDI, async) → InventoryEventBroadcaster → InventoryEventsReceiver → `inventoryChanged` event → browser re-fetches GET /shop/inventory-fragment (into `#shop-products`) or GET /admin/inventory-fragment (into `#admin-inventory`) and morphs it in
- **Returns**: never-ending `text/event-stream`
- **Participants**: 2 (Customer, Admin)

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
- **Returns**: `{route: ShopPage, vm}` JSON (200 fresh page; 400/409 with errors), rendered into `#app`
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

## Replenishment Flows (Locations ← DC)

### Location Request
**File**: `location-request.puml`
- **Trigger**: POST /locations/{id}/requests (store / online FC page)
- **Flow**: LocationReceiver → `StockRequest.parse()` → ReplenishmentHandler → ReplenishmentService → PostgreSQL (DC row locked first; transfer + request in one transaction) + AuditLogService → MongoDB; each transfer then goes to the carrier (`in-transit.puml`)
- **Actions**: stores the request, then shares what the DC has among all pending requests of the product in proportion to what each still needs (`FairShare`), each share shipped (in transit until it arrives); the rest stays PENDING until the next delivery to the DC is shared the same way
- **Returns**: 200 empty body; 400 / 409 `{route: OrderErrors, vm}`
- **Participants**: 1 (Store / Online manager)
- **Databases**: PostgreSQL (`stock`, `replenishment_request`), MongoDB (audit log)

### Admin Decides on a Pending Request
**File**: `admin-decide-request.puml`
- **Trigger**: POST /admin/requests/{id}/fulfil or /reject
- **Flow**: AdminReceiver → ReplenishmentHandler → ReplenishmentService → PostgreSQL + AuditLogService → MongoDB
- **Actions**: fulfil ships what the DC has to this request, ahead of the others; reject cancels what is outstanding
- **Returns**: 200 empty body; 409 if the request is no longer pending
- **Participants**: 1 (Head office)
- **Databases**: PostgreSQL (`stock`, `replenishment_request`), MongoDB (audit log)

### Admin Resets the Demo Data
**File**: `admin-reset.puml`
- **Trigger**: POST /admin/reset (*Reset demo data* button, after a confirm dialog)
- **Flow**: AdminReceiver → ResetHandler → ResetService → PostgreSQL + AuditLogService → MongoDB
- **Actions**: deletes the stock of every location, all requests, shipments and supplier orders (sequences stay), clears the audit log, fires `InventoryReset` so the pages refresh
- **Returns**: 200 empty body
- **Participants**: 1 (Head office)
- **Databases**: PostgreSQL (`stock`, `replenishment_request`, `shipment`, `supplier_order`), MongoDB (audit log)

### Automatic Replenishment
**File**: `auto-replenishment.puml`
- **Trigger**: `StockDeducted` (after a sale) or the demand period timer (`DemandPeriodReceiver`, then `LevelsRecalculated`)
- **Flow**: inbound-event Receiver → ReorderPolicyHandler / ReplenishmentHandler → InventoryService / ReplenishmentService → PostgreSQL + AuditLogService → MongoDB
- **Actions**: period close learns avg/min/max per location and product; a location below its min requests up to max from the DC (AUTOMATIC origin); the DC stock is then shared fairly - after a period close among all locations at once
- **Returns**: nothing (async)
- **Participants**: none (timer, CDI events)
- **Databases**: PostgreSQL (`stock`, `replenishment_request`), MongoDB (audit log)

### In-Transit Transfers (Carrier)
**File**: `in-transit.puml`
- **Trigger**: every transfer from the DC (location request, automatic request, `fulfilPending`, head-office fulfil); catch-up at the period close (`ShipmentCatchUpReceiver`)
- **Flow**: ReplenishmentHandler → CarrierService → `shipments` → CarrierStub (waits `carrier-stub.transit-time`) → `shipment-arrivals` → ShipmentArrivalReceiver → ReplenishmentHandler.receiveShipment → ReplenishmentService → PostgreSQL + AuditLogService → MongoDB
- **Actions**: the DC stock leaves at the transfer as an IN_TRANSIT shipment; the location books it when the arrival is reported - once, a repeated arrival is ignored; shipments in transit for more than 2 min are sent again at the period close
- **Returns**: nothing (async)
- **Participants**: none (Kafka, CDI events)
- **Databases**: PostgreSQL (`stock`, `shipment`), MongoDB (audit log)

### Automatic Supplier Orders
**File**: `auto-purchasing.puml`
- **Trigger**: `DcDemandChanged` (a store / the online FC requested from the DC) or `LevelsRecalculated` (after a period close)
- **Flow**: AutoPurchasingReceiver → PurchasingHandler → SupplierOrderService → PostgreSQL; then the commodity Handler's `place` → supplier, like a manual order
- **Actions**: the DC's position (available + open supplier orders − pending requests) below its learned min → AUTOMATIC supplier order up to max; the delivery closes it, oldest first
- **Returns**: nothing (async)
- **Participants**: none (CDI events)
- **Databases**: PostgreSQL (`stock`, `replenishment_request`, `supplier_order`), MongoDB (audit log)

### Seeding the DC
**File**: `dc-seed.puml`
- **Trigger**: a one-off timer 5 s after the start (`inventory.dc-seed.startup-delay`), `InventoryReset` (after the admin reset) or `LevelsRecalculated` (every period close)
- **Flow**: DcSeedReceiver → PurchasingHandler.seedDc → SupplierOrderService.openSeed → PostgreSQL; then the commodity Handler's `place` → supplier, like a manual order
- **Actions**: per catalog product the DC has no stock row and no open supplier order of (under an advisory lock): a SEED order of 500 - all 28 after a start or a reset, later only what a supplier that was down could not take; the deliveries stock the DC, the locations pull at the next period close
- **Returns**: nothing (async)
- **Participants**: none (CDI events)
- **Databases**: PostgreSQL (`stock`, `supplier_order`), MongoDB (audit log)

### Automatic Tills
**File**: `auto-tills.puml`
- **Trigger**: `OccupancyChanged(store)` (a newer occupancy report was stored); off with `inventory.auto-tills.enabled=false` (tests, e2e)
- **Flow**: AutoTillsReceiver → AutoTillsHandler (cooldown: no decision until a report measured 10 s after the store's last change) → `TillPolicy.decide()` → CheckoutSystemService → `PUT /cashpoint-stub/stores/{id}/tills` (the stores' checkout system, external) → applied by the stub's next tick, reported on `store-occupancy` right away
- **Actions**: one till more when more customers queue than tills are open, or the store is full and every till busy; one less when nobody queues and two tills are free; 1..8 tills
- **Returns**: nothing (async)
- **Participants**: none (CDI events)
- **Databases**: PostgreSQL (`store_occupancy`, read), MongoDB (audit log); the new tills reach PostgreSQL with the next occupancy report

## Event-Driven Flow (Kafka Inbound)

### Store Occupancy
**File**: `store-occupancy.puml`
- **Trigger**: External checkout system publishes to `store-occupancy` (every 5 s per store, key = storeId)
- **Technology**: Kafka snapshot → StoreOccupancyReceiver → OccupancyHandler → PostgreSQL (upsert if newer) → `OccupancyChanged` → SSE `occupancyChanged-{storeId}` → GET /locations/{id}/occupancy-fragment
- **Flow**: a state snapshot, not an event - an older or repeated report changes nothing (no inbox needed); valid reports are not audit-logged
- **Participants**: External checkout system (producer), StoreOccupancyReceiver (consumer), Store manager (page)
- **Databases**: PostgreSQL (`store_occupancy`), MongoDB (invalid reports only)

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

**For instructions on keeping these diagrams in sync with code changes**, see `../../ai/maintaining-flows.md`.
