# Architecture Flow - Kafka Integration Reference

Technical reference for understanding the Kafka-based integration patterns and topic configurations in the supermarket inventory system.

**For human-readable flow diagrams showing all primary flows**, see `architecture-flow.md`.

**For instructions on keeping this file in sync with code changes**, see `../ai/maintaining-architecture-flow.md`.

---

## Data Persistence

### PostgreSQL (outbound-postgres)
- **InventoryService**: Manages stock per location (DC, 3 stores, online FC)
  - `addAmount(location, …)`: Not used by the deliveries any more (see SupplierOrderService); tests seed stock with it
  - `deductAll(location, quantities, OnShortage)`: Called by PurchaseHandler - one transaction, rows locked; `REJECT` for online checkouts (shop, JSON API, online FC: nothing deducted on a shortage), `CAP_AT_ZERO` for cashpoint sales (the message's store; never rejects, stock floors at 0)
  - `recordDemand(location, quantities)`: Called by PurchaseHandler after every sale, own transaction (lost online sales count too)
  - `closePeriod()`: Called by ReorderPolicyHandler at the end of each demand period - learns avg/min/max per row, the DC's included
  - `findAll(location)`, `findAllLocations()`: Called by product list endpoints and the admin matrix
  - Storage: `stock` table (StockTable)
- **ReplenishmentService**: Requests of the stores / online FC to the DC, and the transfers serving them
  - `allocate(productName)`: Called by ReplenishmentHandler after each delivery (via the async `DeliveredToDc` event and inbound-event's `DeliveryEventReceiver`) and after automatic requests - shares the DC stock among all pending requests in proportion to what each still needs (`FairShare`)
  - `request`, `fulfil`, `reject`: Called from the location page and head office; each is one transaction with the stock transfer
  - A transfer takes the stock off the DC and records an IN_TRANSIT shipment; `receiveShipment(id)` (called via ShipmentArrivalReceiver) adds it to the location once - a repeated arrival changes nothing; `findInTransit(before)` serves the redispatch catch-up
  - Storage: `shipment` table (ShipmentTable)
  - `requestIfLow(location, productName)`: Called by ReplenishmentHandler for automatic replenishment (after a sale, after a period close) - position = available + in transit + outstanding requests; creates the request only, `allocate` serves it
  - Storage: `replenishment_request` table (ReplenishmentRequestTable)
- **SupplierOrderService**: The DC's orders from suppliers, and the deliveries that close them
  - `receiveDelivery(productName, type, quantity)`: Called by InventoryHandler for every Kafka delivery - adds it to the DC and closes the open orders of the product oldest first (deliveries carry no order id), one transaction
  - `open(…)`: Called by the commodity Handlers before an order is sent (the delivery may arrive first); `cancel(id)` if sending failed
  - `openIfLow(productName)`: Called by PurchasingHandler for automatic supplier orders (after a request to the DC, after a period close) - position = available + open orders − pending requests, below min → AUTOMATIC order up to max
  - `openSeed(products, quantity)`: Called by PurchasingHandler.seedDc (5 s after the start, after the admin reset, at a period close) - one SEED order per catalog product the DC has no stock row and no open order of; a Postgres advisory lock serializes concurrent seeds
  - Storage: `supplier_order` table (SupplierOrderTable)
- **OccupancyService**: The latest occupancy each store's checkout system reported
  - `saveIfNewer(occupancy)`: Called by OccupancyHandler for every `store-occupancy` message - one upsert (`on conflict … do update … where excluded.measuredAt > store_occupancy.measuredAt`), so an older or repeated report changes nothing and needs no lock
  - `findAll()`: Called by the `/locations` page and its occupancy fragments
  - `appendToHistory(occupancy, keepSince)`: Called by OccupancyHandler after a stored report - `insert … on conflict (storeId, measuredAt) do nothing`, then the store's rows older than 30 min deleted
  - `history(store, since)`: Called by the occupancy fragments (the charts, last 10 min)
  - Storage: `store_occupancy` table (StoreOccupancyTable, migration V4) - one row per store; `store_occupancy_history` (StoreOccupancyHistoryTable, migration V6) - every report of the last 30 min; neither is touched by the admin reset (the external system's state)

- **PurchaseService**: The completed purchases per location, for the Purchases tables on `/locations`
  - `append(location, purchase, purchasedAt, keepSince)`: Called by PurchaseHandler after every completed purchase (every cashpoint sale, an online checkout unless rejected) - one row (distinct products, units), then the location's rows older than 30 min deleted; a redelivered cashpoint message adds a second row
  - `findRecent(location, limit)`: Called by the `/locations` inventory fragments (latest 10)
  - Storage: `purchase` table (PurchaseTable, migration V7); deleted by the admin reset

### MongoDB (outbound-mongodb)
- **AuditLogService**: Logs all system events
  - `log()`: Called by handlers to record operations
  - `findRecent()`: Called by audit-log endpoints
  - Storage: `audit_log` collection (Panache entity: `AuditLogEntryEntity`, distinct from core's `AuditLogEntry` domain record)

## External System Integrations (Outbound)

### REST API Clients (outbound-httpclient)
- **FruitSupplierService** → FruitSupplierClient
- **VegetablesSupplierService** → VegetablesSupplierClient
- **DairySupplierService** → DairySupplierClient

All endpoint: `placeOrder(productName, quantity)`

- **CheckoutSystemService** → CheckoutSystemClient (`checkout-system`): `setTills(store, tills)` → `PUT /cashpoint-stub/stores/{storeId}/tills` `{"tills": n}` (CashpointTillsStub; 204, 400 outside 1..8, 404 unknown store). Called by AutoTillsHandler (automatic tills, on `OccupancyChanged`)

### SOAP Web Services (outbound-webservice)
- **BeverageSupplierService** → BeverageOrderService (SOAP)
- **MeatSupplierService** → MeatOrderService (SOAP)
- **BakerySupplierService** → BakeryOrderService (SOAP)

All endpoint: `placeOrder(productName, quantity)`

### Kafka Producer (outbound-kafka)
- **NonFoodSupplierService** → Emitter (nonfood-orders-out channel)
  - Publishes: NonFoodOrderMessage to `nonfood-orders` topic
- **CarrierService** → Emitter (shipments-out channel)
  - Publishes: ShipmentMessage (shipmentId, locationId, productName, quantity) to `shipments` topic

## Kafka Topic Cycles: Request-Response Through Events

These cycles show how external supplier integrations (REST/SOAP stubs) are decoupled using Kafka topics:

Every stub publishes its delivery after the supplier lead time (`supplier-stub.lead-time`, 30s ± 20% in dev, 0 in `%test` and e2e), so the DC's supplier order stays OPEN until then.

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

### Two-Topic Carrier Cycle (DC → stores / online FC)

**Topic 1: shipments (Dispatch)**
- **Producer**: CarrierService (in outbound-kafka)
- **Consumer**: CarrierStub (in external-outbound-kafka)
- **Trigger**: ReplenishmentHandler, after every transfer committed (request, automatic request, fulfilPending, head-office fulfil) and for overdue shipments at the period close (`redispatchOverdue`)
- **Message**: `{shipmentId, locationId, productName, quantity}` - one topic for all commodities
- **Config**:
  - Outgoing: `mp.messaging.outgoing.shipments-out.topic=shipments`
  - Incoming (stub): `mp.messaging.incoming.shipments.topic=shipments`

**Topic 2: shipment-arrivals (Arrival)**
- **Producer**: CarrierStub (in external-outbound-kafka), after `carrier-stub.transit-time` (20s ± 20% in dev, 0 in `%test` and e2e)
- **Consumer**: ShipmentArrivalReceiver (in inbound-kafka) → ReplenishmentHandler.receiveShipment(shipmentId)
- **Message**: `{shipmentId}`; null payload or missing `shipmentId` goes to the DLQ (`shipment-arrivals-dlq`); an unknown or already arrived shipment is audit-logged `SHIPMENT_ARRIVAL_IGNORED` (idempotent)
- **Config**:
  - Outgoing (stub): `mp.messaging.outgoing.shipment-arrivals-out.topic=shipment-arrivals`
  - Incoming: `mp.messaging.incoming.shipment-arrivals.topic=shipment-arrivals`

### Cashpoint Purchase Cycle

**Topic: cashpoint-purchases**
- **Producer**: External checkout systems (simulated by CashpointStub: one purchase per customer who pays at a store's till - per store a `StoreSimulation` with its capacity and tills - a full store turns new customers away; the basket comes from the store's stock, read from `GET /api/locations/{id}/products`)
- **Consumer**: CashpointReceiver (in inbound-kafka)
- **Message**: `{storeId, items: [{productName, quantity}]}`; a missing `storeId` goes to the DLQ, an id that is no store is audit-logged `INVALID` and skipped
- **Flow**: Cashpoint event → PurchaseHandler.recordStoreSale(store, …) → that store's stock deducted (capped at 0; overselling is logged as `STOCK_DISCREPANCY`, never rejected), the sold quantities recorded as demand, the sale stored for the store's Purchases table
- **Config**: 
  - Incoming: `mp.messaging.incoming.cashpoint-purchases.topic=cashpoint-purchases`
  - Outgoing (for testing): `mp.messaging.outgoing.cashpoint-purchases-out.topic=cashpoint-purchases`

### Store Occupancy (state snapshots)

**Topic: store-occupancy** (Kafka key: storeId)
- **Producer**: External checkout systems (simulated by CashpointStub: every 5 s per store, and right after a till change; `StoreSimulation.report`)
- **Consumer**: StoreOccupancyReceiver (in inbound-kafka)
- **Message**: `{storeId, measuredAt, inside, capacity, queuing, tills, tillsBusy, paid, turnedAway}` - current values only (`paid` at a till / `turnedAway` at the full store: in the last demo day, not since the last report). A missing field goes to the DLQ; an id that is no store, or values out of range, are audit-logged `INVALID` and skipped. Valid messages are not audit-logged (36 per minute)
- **Flow**: Occupancy report → OccupancyHandler.record → OccupancyService.saveIfNewer → if newer, appendToHistory and `OccupancyChanged` (CDI, async) → SSE `occupancyChanged-<storeId>` on `/inventory/events` → that store's line on `/locations` re-fetches; and AutoTillsReceiver → AutoTillsHandler → `TillPolicy` → CheckoutSystemService (opens/closes a till, which the stub reports right away)
- **A snapshot, not an event**: unlike every other topic here, only the latest message per store counts. Storing it is idempotent (an older or redelivered report is not newer), so it needs no inbox, not even with two pods. Keyed by store, the topic suits **log compaction** (`cleanup.policy=compact`: Kafka keeps at least the latest message per key) - not configured in this demo, where the dev-services topic keeps everything for its short life
- **Config**:
  - Incoming: `mp.messaging.incoming.store-occupancy.topic=store-occupancy`
  - Outgoing (stub): `mp.messaging.outgoing.store-occupancy-out.topic=store-occupancy`
  - Outgoing (for testing): `mp.messaging.outgoing.testing-store-occupancy-out.topic=store-occupancy`

## Summary of All Endpoints (Including Indirect Kafka Flows)

| Receiver | Route | Method | Flow Type | Kafka Topic Connection | Data Sinks |
|----------|-------|--------|-----------|------------------------|-----------|
| (static resource) | / | GET | Landing page: links to /admin, /locations, /shop, /audit-log | - | - |
| AdminReceiver | /admin | GET | Static page shell | - | - |
| AdminReceiver | /admin/page | GET | Query | - | PostgreSQL (read) |
| AdminReceiver | /admin/inventory-fragment | GET | Query | - | PostgreSQL (read) |
| AdminReceiver | /admin/requests-fragment | GET | Query | - | PostgreSQL (read) |
| AdminReceiver | /admin/requests/{id}/fulfil, /reject | POST | Command | - | PostgreSQL + MongoDB |
| AdminReceiver | /admin/reset | POST | Command (dev: deletes all demo data and the audit log) | - | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-fruits | POST | Command | **→ fruit-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-vegetables | POST | Command | **→ vegetables-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-dairy | POST | Command | **→ dairy-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-beverages | POST | Command | **→ beverages-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-meat | POST | Command | **→ meat-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-bakery | POST | Command | **→ bakery-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| AdminReceiver | /admin/order-nonfood | POST | Command | **→ nonfood-orders** → **← nonfood-deliveries** | PostgreSQL + MongoDB |
| ShopReceiver | /shop | GET | Static page shell | - | - |
| ShopReceiver | /shop/page | GET | Query | - | PostgreSQL (read) |
| ShopReceiver | /shop/inventory-fragment | GET | Query | - | PostgreSQL (read) |
| InventoryEventsReceiver | /inventory/events | GET | SSE stream (inventory changes, for /shop, /admin and /locations; `occupancyChanged-<storeId>` for /locations) | - | - |
| ShopReceiver | /shop/checkout | POST | Command | - | PostgreSQL + MongoDB |
| AuditLogReceiver | /audit-log | GET | Static page shell (Refresh button, no SSE) | - | - |
| AuditLogReceiver | /audit-log/page | GET | Query (latest 300 entries) | - | MongoDB (read) |
| LocationReceiver | /locations | GET | Static page shell | - | - |
| LocationReceiver | /locations/page, /locations/{id}/inventory-fragment | GET | Query | - | PostgreSQL (read) |
| LocationReceiver | /locations/{id}/requests | POST | Command | - | PostgreSQL + MongoDB |
| LocationReceiver | /locations/{id}/occupancy-fragment | GET | Query (stores only) | (fed by **← store-occupancy**) | PostgreSQL (read) |
| ProductApiReceiver | /api/products | GET | Query | - | PostgreSQL (read) |
| ProductApiReceiver | /api/products/order-fruits | POST | Command | **→ fruit-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-vegetables | POST | Command | **→ vegetables-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-dairy | POST | Command | **→ dairy-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-beverages | POST | Command | **→ beverages-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-meat | POST | Command | **→ meat-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-bakery | POST | Command | **→ bakery-deliveries** (stub publishes) | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/order-nonfood | POST | Command | **→ nonfood-orders** → **← nonfood-deliveries** | PostgreSQL + MongoDB |
| ProductApiReceiver | /api/products/purchase | POST | Command | - | PostgreSQL + MongoDB |
| LocationApiReceiver | /api/locations/{id}/products | GET | Query | - | PostgreSQL (read) |
| FruitDeliveryReceiver | **← fruit-deliveries** | Event | Delivery | Consumes: **fruit-deliveries** | PostgreSQL + MongoDB |
| VegetablesDeliveryReceiver | **← vegetables-deliveries** | Event | Delivery | Consumes: **vegetables-deliveries** | PostgreSQL + MongoDB |
| DairyDeliveryReceiver | **← dairy-deliveries** | Event | Delivery | Consumes: **dairy-deliveries** | PostgreSQL + MongoDB |
| BeveragesDeliveryReceiver | **← beverages-deliveries** | Event | Delivery | Consumes: **beverages-deliveries** | PostgreSQL + MongoDB |
| MeatDeliveryReceiver | **← meat-deliveries** | Event | Delivery | Consumes: **meat-deliveries** | PostgreSQL + MongoDB |
| BakeryDeliveryReceiver | **← bakery-deliveries** | Event | Delivery | Consumes: **bakery-deliveries** | PostgreSQL + MongoDB |
| NonFoodDeliveryReceiver | **← nonfood-deliveries** | Event | Delivery | Consumes: **nonfood-deliveries** | PostgreSQL + MongoDB |
| CashpointReceiver | **← cashpoint-purchases** | Event | Purchase | Consumes: **cashpoint-purchases** | PostgreSQL + MongoDB |
| ShipmentArrivalReceiver | **← shipment-arrivals** | Event | Arrival | Consumes: **shipment-arrivals** | PostgreSQL + MongoDB |
| StoreOccupancyReceiver | **← store-occupancy** | Event | Snapshot | Consumes: **store-occupancy** | PostgreSQL (MongoDB only if invalid) |
