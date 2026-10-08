# System Participants by Maven Module

Complete inventory of all classes participating in the system flows, organized by Maven module.

**Source**: Derived from architecture-flow.md with module mappings from actual code structure.
**Maintenance**: see `../ai/maintaining-module-participants.md`.

**Package scheme (as of 2026-09-13)**: every module — `core` included — is organized as `org.svenehrke.triptychdemo.feature.<commodity>` (fruit, vegetable, dairy, beverage, meat, bakery, nonfood) or `org.svenehrke.triptychdemo.cross(.<concern>)` for cross-cutting concerns (inventory, auditlog, products, purchase, cashpoint, and the admin/shop/json-api aggregator receivers). There is no `core.application`/`core.api`/`core.spi`/`adapter.inbound.*`/`adapter.outbound.*` package scheme anymore — `core`'s previous `APIs.java`/`SPIs.java` container classes were split into standalone top-level interfaces, one per port, each moved into its feature or cross package. On 2026-09-27 the inbound `*API` interfaces were removed altogether - receivers inject the `*Handler` classes directly (see `concepts.md`, "Why inbound ports have no interface"). `external-*` modules are untouched by this and keep their own `org.svenehrke.triptychdemo.external.*` root (they are not part of the hexagonal architecture — see `concepts.md`).

## Quick Reference: All Modules & Participants

| Module | Participants |
|--------|--------------|
| **inbound-http-html** | `AdminReceiver`<br>`ShopReceiver`<br>`LocationReceiver`<br>`AuditLogReceiver`<br>`InventoryEventsReceiver`<br>`InventoryEventBroadcaster`<br>`ShopCart`<br>`PageShell` |
| **inbound-http-jsonapi** | `ProductApiReceiver`<br>`LocationApiReceiver`<br>`XxxOrderRequest` (+ `OrderRequest`)/`PurchaseRequest`/`PurchaseRequestItem`/`RequestStructureErrorMessages`<br>`JsonInputErrors`/`StrictJsonReader`/`JsonResponses`<br>`ProductJson` |
| **inbound-kafka** | `FruitDeliveryReceiver`<br>`VegetablesDeliveryReceiver`<br>`DairyDeliveryReceiver`<br>`BeveragesDeliveryReceiver`<br>`MeatDeliveryReceiver`<br>`BakeryDeliveryReceiver`<br>`NonFoodDeliveryReceiver`<br>`CashpointReceiver`<br>`ShipmentArrivalReceiver`<br>`StoreOccupancyReceiver` |
| **inbound-event** | `DeliveryEventReceiver`, `AutoReplenishmentReceiver`, `AutoPurchasingReceiver`, `DcSeedReceiver`, `ShipmentCatchUpReceiver`, `DemandPeriodReceiver`, `AutoTillsReceiver`, `EventExecutorProducer` |
| **core** | `FruitSupplierSPI`/`FruitDelivery`/`FruitsHandler`<br>`VegetablesSupplierSPI`/`VegetableDelivery`/`VegetablesHandler`<br>`DairySupplierSPI`/`DairyDelivery`/`DairyHandler`<br>`BeverageSupplierSPI`/`BeverageDelivery`/`BeveragesHandler`<br>`MeatSupplierSPI`/`MeatDelivery`/`MeatHandler`<br>`BakerySupplierSPI`/`BakeryDelivery`/`BakeryHandler`<br>`NonFoodSupplierSPI`/`NonFoodDelivery`/`NonFoodHandler`<br>`InventoryRepositorySPI`/`InventoryHandler`/`InventoryEvent` (`DeliveredToDc`/`StockDeducted`/`ReplenishmentChanged`/`LevelsRecalculated`/`DcDemandChanged`/`SupplierOrdersChanged`/`InventoryReset`)<br>`Location`/`Replenished`/`Warehouse`/`Store`/`OnlineFc`/`Locations`<br>`ReplenishmentRepositorySPI`/`ReplenishmentHandler`/`StockRequest`/`ReplenishmentRequest`/`RequestOrigin`/`CarrierSPI`/`Shipment`/`ShipmentStatus`<br>`ReorderPolicyHandler`/`ReorderPolicy`/`DemandEstimate`/`LearnedLevels`<br>`SupplierOrderRepositorySPI`/`PurchasingHandler`/`SupplierOrder`/`SupplierOrderStatus`/`SupplierOrderOrigin`<br>`AuditLogSPI`/`AuditLogHandler`/`AuditLogEntry`<br>`ResetRepositorySPI`/`ResetHandler`<br>`OccupancyRepositorySPI`/`CheckoutSystemSPI`/`OccupancyHandler`/`StoreOccupancy`/`TillCount`/`OccupancyChanged`<br>`ProductsHandler`/`Product`/`ProductStock`/`ProductType`<br>`PurchaseRepositorySPI`/`PurchaseHandler`/`Purchase`/`PurchaseItem`/`RecordedPurchase`<br>`AsyncEvents`/`EventExecutor` |
| **outbound-postgres** | `InventoryService`<br>`StockTable`<br>`ReplenishmentService`<br>`ReplenishmentRequestTable`<br>`ShipmentTable`<br>`SupplierOrderService`<br>`SupplierOrderTable`<br>`OccupancyService`<br>`StoreOccupancyTable`<br>`StoreOccupancyHistoryTable`<br>`PurchaseService`<br>`PurchaseTable`<br>`ResetService`<br>`Db` |
| **outbound-mongodb** | `AuditLogService`<br>`AuditLogEntryEntity` |
| **outbound-httpclient** | `FruitSupplierService`<br>`VegetablesSupplierService`<br>`DairySupplierService`<br>`CheckoutSystemService`<br>`FruitSupplierClient`<br>`VegetablesSupplierClient`<br>`DairySupplierClient`<br>`CheckoutSystemClient` |
| **outbound-webservice** | `BeverageSupplierService`<br>`MeatSupplierService`<br>`BakerySupplierService`<br>`BeverageOrderService`<br>`MeatOrderService`<br>`BakeryOrderService` |
| **outbound-kafka** | `NonFoodSupplierService`<br>`CarrierService` |
| **external-outbound-rest** | `FruitSupplierStub`<br>`VegetablesSupplierStub`<br>`DairySupplierStub` |
| **external-outbound-soap** | `BeverageSupplierStub`<br>`MeatSupplierStub`<br>`BakerySupplierStub` |
| **external-outbound-kafka** | `NonFoodSupplierStub`<br>`CarrierStub` |
| **external-inbound-kafka** | `CashpointStub`<br>`StoreSimulation`<br>`CashpointTillsStub`<br>`CashpointStubConfig`<br>`ProductsApiClient` |

---

## inbound-http-html

**Purpose**: HTTP inbound adapter for HTML form-based user interfaces
**Package**: `org.svenehrke.triptychdemo.cross` (both receivers are cross-cutting aggregators — Admin touches every commodity's ordering Handler, Shop touches Products+Purchase — so neither lives in a `feature.<name>` package)

### Receivers
- `AdminReceiver` - Admin dashboard in two tabs: *Inventory* (read-only product × location matrix, pending requests, open supplier orders) and *Manual restock* (supplier order forms, DC inventory with restock buttons) (GET /admin shell, GET /admin/page and fragments incl. /admin/dc-inventory-fragment and /admin/supplier-orders-fragment, POST /admin/order-*, POST /admin/requests/{id}/fulfil|reject, POST /admin/reset - the shell's *Reset demo data* button, `hx-confirm`)
- `ShopReceiver` - Customer shopping interface, sells the online FC's stock (GET /shop shell, GET /shop/page and fragment, POST /shop/checkout)
- `LocationReceiver` - One page for all stores and the online FC: stock, the latest purchases, requests to the DC, per store its occupancy, with an "auto" tag while `inventory.auto-tills.enabled` (GET /locations shell, GET /locations/page, GET /locations/{id}/inventory-fragment, POST /locations/{id}/requests, GET /locations/{id}/occupancy-fragment)
- `AuditLogReceiver` - The latest 300 audit log entries, newest first, without SSE: the shell polls them every 2 s (GET /audit-log shell, GET /audit-log/page)
- `InventoryEventsReceiver` - GET /inventory/events SSE stream (`inventoryChanged`), used by the shop, admin and locations shells
- `InventoryEventBroadcaster` (package-private) - `@ObservesAsync InventoryEvent` (every kind), re-published as a JDK `Flow.Publisher` that the SSE streams subscribe to
- `PageShell` (package-private) - fills the `{{key}}` placeholders of a page shell
- `META-INF/resources/index.html` - static landing page at `/`, linking /admin, /locations and /shop
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
- `ProductApiReceiver` - REST API endpoints (GET /api/products, POST /api/products/order-*, POST /api/products/purchase); products and purchases are the online FC's
- `LocationApiReceiver` - GET /api/locations/{id}/products (stock of one location; 404 for an unknown id)

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
- `ProductJson` - the product list's JSON shape (name, type, availableAmount); core's `Product` is not serialized directly, so a change inside core can't silently change the API

**Responsibilities**:
- Parse HTTP JSON requests (APPLICATION_JSON)
- Deserialize JSON into request objects
- Route to the appropriate core Handlers
- Return JSON responses
- Validate API input contracts

**Technology**: Quarkus REST (JAX-RS), Jackson (JSON serialization)

---

## inbound-event

**Purpose**: Inbound adapter for domain events that core fires as CDI async events (`AsyncEvents`, i.e. `Event.fireAsync` on a virtual thread) - in-process, like `inbound-kafka` is for messages
**Package**: `org.svenehrke.triptychdemo.cross.inventory`, `org.svenehrke.triptychdemo.cross.replenishment`, `org.svenehrke.triptychdemo.cross.reorder`, `org.svenehrke.triptychdemo.cross.purchasing`, `org.svenehrke.triptychdemo.cross.occupancy`, `org.svenehrke.triptychdemo.cross.events`

### Receivers
- `DeliveryEventReceiver` - `@ObservesAsync DeliveredToDc` → `ReplenishmentHandler.fulfilPending(productName)`; audit-logs a failure (`FULFIL_PENDING_FAILED`) instead of letting it vanish, the requests then stay pending
- `AutoReplenishmentReceiver` - `@ObservesAsync StockDeducted` → `ReplenishmentHandler.replenishIfLow(location, productNames)`; `@ObservesAsync LevelsRecalculated` → `replenishAllIfLow(DC products)` - all locations at once, so a shortfall is shared (fills empty locations at cold start); failures audit-logged (`AUTO_REPLENISHMENT_FAILED`); off with `inventory.auto-replenishment.enabled=false` (`%test`, e2e)
- `ShipmentCatchUpReceiver` - `@ObservesAsync LevelsRecalculated` → `ReplenishmentHandler.redispatchOverdue(inventory.shipment-redispatch-after)` - sends shipments still in transit after 2 min to the carrier again (a lost dispatch or arrival); failures audit-logged (`REDISPATCH_FAILED`)
- `AutoPurchasingReceiver` - `@ObservesAsync DcDemandChanged` → `PurchasingHandler.orderIfLow(productName)`; `@ObservesAsync LevelsRecalculated` → `orderIfLow` per DC product (one at a time, so a supplier that is down does not block the others); failures audit-logged (`AUTO_PURCHASING_FAILED`); off with `inventory.auto-purchasing.enabled=false` (`%test`, e2e) - a switch of its own, so a flow test can turn on one stage only
- `DcSeedReceiver` - `StartupEvent` (one-off Vert.x timer, `inventory.dc-seed.startup-delay` = 5s, then on a virtual thread), `@ObservesAsync InventoryReset` and `LevelsRecalculated` → `PurchasingHandler.seedDc(inventory.dc-seed.quantity = 500)` - seeds what the DC neither carries nor has on order: everything 5 s after the start (once the HTTP server listens) and after the admin reset; later it re-orders what a supplier that was down could not take; failures audit-logged (`DC_SEED_FAILED`); off with `inventory.dc-seed.enabled=false` (`%test`, e2e)
- `AutoTillsReceiver` - `@ObservesAsync OccupancyChanged` → `AutoTillsHandler.adjust(store)`; failures audit-logged (`AUTO_TILLS_FAILED`); off with `inventory.auto-tills.enabled=false` (`%test`, e2e)
- `DemandPeriodReceiver` - `@Scheduled(every = "${inventory.demand-period}", delayed = "${inventory.first-period-close-delay}")` (1 min, `off` in `%test` and e2e; the first close one period after the start - no zero-length period, no supplier call before the HTTP server listens) → `ReorderPolicyHandler.closePeriod()`; needs `quarkus-scheduler`

### Event executor
- `EventExecutorProducer` - Produces core's `@EventExecutor` as Quarkus' `@VirtualThreads ExecutorService`, so core's async events (and with them every observer above) run on a virtual thread - one per event, its observers one after another (see `virtual-threads-in-this-project.md`)

**Responsibilities**:
- React to core's domain events, decoupled from the code that fires them (a failing reaction cannot fail, or make the Kafka receiver repeat, the delivery)
- Route to Handlers only, like every Receiver (ArchUnit's inbound rules cover this module by its `inbound-` name)

**Technology**: CDI (Quarkus ArC) async events, delivered on virtual threads (`quarkus-virtual-threads`), no further dependency besides `core`

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
- `CashpointReceiver` (`cross.cashpoint`) - Consumes from `cashpoint-purchases` topic (customer purchases from a store's checkout; `storeId` missing → DLQ, not a store → audit-logged INVALID); also in this package: `PurchaseMessage`, `PurchaseMessageItem`, `PurchaseMessageDeserializer`
- `ShipmentArrivalReceiver` (`cross.replenishment`) - Consumes from `shipment-arrivals` topic (the carrier reports a DC shipment as arrived) → `ReplenishmentHandler.receiveShipment(shipmentId)`; a repeated arrival is booked once; null payload / missing `shipmentId` → DLQ; also `ShipmentArrivalMessage`, `ShipmentArrivalMessageDeserializer`
- `StoreOccupancyReceiver` (`cross.occupancy`) - Consumes from `store-occupancy` topic (a store's occupancy snapshot, keyed by store) → `OccupancyHandler.record`; a missing field → DLQ, not a store / out of range → audit-logged INVALID; valid reports are not audit-logged; also `OccupancyMessage`, `OccupancyMessageDeserializer`

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
- `FruitsHandler` - Handles fruit orders (methods: order - records a MANUAL supplier order, then places it; place(SupplierOrder) - sends a recorded order, manual or automatic (PurchasingHandler), cancels it if the supplier fails, fires `SupplierOrdersChanged`; injects FruitSupplierSPI + SupplierOrderRepositorySPI + AuditLogSPI)

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
- `InventoryRepositorySPI` - Interface for stock per location (methods: findAll(location), findAllLocations, addAmount(location, …) - seeding/tests, deliveries go through SupplierOrderRepositorySPI, deductAll(location, …), recordDemand(location, …) - own transaction, also for rejected/capped sales, closePeriod - one transaction per row, the DC's rows included)
- `LocationStock` - Domain record (location, product, estimate, levels), returned by findAllLocations
- `InventoryEvent` - Sealed interface of the CDI events core fires via `AsyncEvents` (`fireAsync`), one per committed change: `DeliveredToDc(productName)` (InventoryHandler), `StockDeducted(location, productNames)` (PurchaseHandler), `ReplenishmentChanged(location)` (ReplenishmentHandler), `LevelsRecalculated()` (ReorderPolicyHandler), `DcDemandChanged(productName)` (ReplenishmentHandler, on every request created), `SupplierOrdersChanged()` (commodity Handlers), `InventoryReset()` (ResetHandler). Observed by inbound adapters only - specific ones (DeliveryEventReceiver, AutoReplenishmentReceiver, AutoPurchasingReceiver) or all (InventoryEventBroadcaster, for the SSE live updates). All of them live in this package: core is no named module, so a sealed type's subclasses must share its package
- `OnShortage` - Enum passed to deductAll: `REJECT` (online, deduct nothing) | `CAP_AT_ZERO` (physical store)
- `StockDeduction` - Result of deductAll (updated products, shortages)
- `Shortage` - Domain record (productName, requested, available) with rejection and discrepancy messages
- `InventoryHandler` - Adds deliveries to the DC and closes open supplier orders of the product oldest first (`SupplierOrderRepositorySPI.receiveDelivery`, one transaction; audit `SUPPLIER_ORDER_DELIVERED`), then fires `DeliveredToDc` asynchronously (see inbound-event), for all commodities (methods: updateFruitAmount, updateVegetableAmount, updateDairyAmount, updateBeverageAmount, updateMeatAmount, updateBakeryAmount, updateNonFoodAmount) - imports each commodity's `*Delivery` record from its `feature.<commodity>` package

### cross.auditlog
- `AuditLogSPI` - Interface for audit log persistence (methods: log, findRecent, clear)
- `AuditLogHandler` - Retrieves audit log entries (method: recent)
- `AuditLogEntry` - Domain record (event, details, timestamp) - not to be confused with `outbound-mongodb`'s `AuditLogEntryEntity` (the Panache persistence entity); the two used to share the name `AuditLogEntry` until 2026-09-13, when the entity was renamed to avoid a fully-qualified-name collision once both landed in `cross.auditlog`

### cross.products
- `ProductsHandler` - Lists products (methods: listAll(location); listAllLocations - one `ProductStock` per product with its stock at every location; injects InventoryRepositorySPI)
- `ProductStock` - Domain record (name, type, byLocation: `LocationStock` per location; availableAt, estimateAt, levelsAt)
- `Product` - Domain record (name, type, availableAmount)
- `ProductType` - Enum (FRUIT, VEGETABLE, DAIRY, BEVERAGE, MEAT, BAKERY, NON_FOOD)
- `Catalog` - `PRODUCTS`: the 28 products the chain lists (`CatalogProduct` name + type, 4 per type) - what the admin order forms offer and what the DC is seeded with; only a list, orders of other names stay allowed

### cross.purchase
- `PurchaseOutcome` - Sealed result of checkout (`Completed` | `Rejected`)
- `PurchaseHandler` - Handles customer purchases (methods: checkout - online, rejects on insufficient stock; recordStoreSale(store, …) - physical store, never rejects; both record the requested quantities as demand and store a completed purchase via PurchaseRepositorySPI, before firing `StockDeducted`; listRecent(location, limit) - for `/locations`; injects InventoryRepositorySPI + PurchaseRepositorySPI + AuditLogSPI)
- `PurchaseItem` - Domain record (productName, quantity)
- `Purchase` - Domain record (items); `products()` - distinct products, `units()` - total quantity
- `PurchaseRepositorySPI` - The completed purchases per location, for `/locations` (methods: append(location, purchase, purchasedAt, keepSince) - drops the location's purchases before `keepSince`, findRecent(location, limit) - newest first)
- `RecordedPurchase` - A stored purchase (id, location, products, units, purchasedAt)

### cross.location
- `Location` - Sealed interface (id, name): `Warehouse` | `Replenished`, so an operation only one kind supports takes that type
- `Replenished` - Sealed sub-interface for the locations replenished from the DC: `Store` | `OnlineFc` (e.g. `StockRequest`, the location pages)
- `Warehouse`, `Store`, `OnlineFc` - Records (id, name); `PurchaseHandler.recordStoreSale` takes a `Store`
- `Locations` - The fixed set as a domain constant: DC, Zurich, Bern, Basel, Online FC (`ALL`, `REPLENISHED`, `byId`, `replenishedById`, `storeById`, `of`, `replenishedOf`)

### cross.replenishment
- `ReplenishmentRepositorySPI` - Requests to the DC and the transfers serving them, each use case one transaction (methods: request, requestIfLow - automatic: checks the learned levels and creates the request in one locked transaction, allocate - shares the DC stock among all pending requests of the product (`FairShare`), fulfil, reject, receiveShipment - books an arrival once (empty if unknown or arrived already), findInTransit, findPending, findRecent); a request that gets less than it needs stays pending; a transfer takes the stock off the DC and records an IN_TRANSIT shipment, the location's stock grows only on arrival
- `ReplenishmentHandler` - Pull replenishment; fires `DcDemandChanged` whenever a request is created (methods: request, replenishIfLow / replenishAllIfLow - via AutoReplenishmentReceiver, after a sale / a period close, fulfilPending - after a delivery, via DeliveryEventReceiver, fulfil/reject - head office, receiveShipment - via ShipmentArrivalReceiver, redispatchOverdue - via ShipmentCatchUpReceiver, listPending, listRecent); hands every shipment to `CarrierSPI` after its transaction committed (audit `STOCK_SHIPPED`; a failing carrier is audit-logged `SHIPMENT_DISPATCH_FAILED`, not rethrown); audit `SHIPMENT_ARRIVED` / `SHIPMENT_ARRIVAL_IGNORED` / `SHIPMENT_REDISPATCHED`
- `StockRequest` / `ParsedStockRequest` - What a location asks for (location, productName, quantity 1-2000), via `parse()` like the supplier orders
- `ReplenishmentRequest` - Stored request (id, location, productName, requested, shipped, status, origin, createdAt); FULFILLED once everything is shipped
- `RequestStatus` - Enum (PENDING, FULFILLED, REJECTED)
- `RequestOrigin` - Enum (MANUAL, AUTOMATIC)
- `Transfer` - Result of a transfer (request afterwards, the shipment it made)
- `Requested` - Result of `request` / `fulfil` (the request afterwards, the transfers the operation made)
- `Shipment` - Goods on their way from the DC to a location (id, requestId, location, productName, quantity, status, dispatchedAt, arrivedAt)
- `ShipmentStatus` - Enum (IN_TRANSIT, ARRIVED)
- `CarrierSPI` - The carrier between the DC and the locations (method: dispatch); reports arrivals back asynchronously (ShipmentArrivalReceiver)
- `FairShare` - Pure: splits DC stock among requests in proportion to outstanding (largest remainder, ties to the oldest)

### cross.reorder
- `ReorderPolicy` - Domain constants per location type (α, z, lead time L, covered periods R, initial avg), chosen by a switch over `Location`; the DC's demand is what the locations request from it
- `DemandEstimate` - Exponentially smoothed mean/variance of the demand per period (`initial`, `next` - pure)
- `LearnedLevels` - min (reorder point) / max (order-up-to), rounded up (`of`, `reorderQuantity(available, outstanding)` - pure)
- `ReorderPolicyHandler` - closePeriod: `InventoryRepositorySPI.closePeriod()`, audit `PERIOD_CLOSED`, fires `LevelsRecalculated`

### cross.purchasing
- `SupplierOrderRepositorySPI` - The DC's supplier orders, each method one transaction with the DC stock row locked first (methods: open - records an order before it is sent, openIfLow - automatic: position = available + open orders − pending requests (the DC's backorders), below min → AUTOMATIC order up to max (≤ 2000), checked and recorded in one locked transaction, cancel, receiveDelivery - DC stock + close open orders oldest first, findOpen, openSeed - one SEED order per catalog product the DC has no stock row and no open order of, under a Postgres advisory lock since there is no row to lock yet)
- `PurchasingHandler` - Central purchasing (methods: orderIfLow(productName) - via AutoPurchasingReceiver, `openIfLow`, audit `AUTO_SUPPLIER_ORDER_CREATED`, then the commodity Handler's `place` chosen by `ProductType`; seedDc(quantity) - via DcSeedReceiver, `openSeed`, audit `DC_SEEDED`, places every order even if one fails (a failed one is cancelled, so the next seed orders it again), rethrows the first failure; listOpen)
- `SupplierOrder` - Stored order (id, productName, type, quantity, delivered, status, origin, createdAt; outstanding, describe)
- `SupplierOrderStatus` - Enum (OPEN, DELIVERED, CANCELLED)
- `SupplierOrderOrigin` - Enum (MANUAL, AUTOMATIC)

### cross.occupancy
- `StoreOccupancy` - What a store's checkout system reports (store, measuredAt, inside, capacity, queuing, tills 1..8, tillsBusy, paid and turnedAway in the last demo day); `parse()` / sealed `ParsedStoreOccupancy`. A snapshot: only the latest per store counts
- `TillCount` - How many tills a store should have open (1..`MAX_TILLS` = 8); only built by the app, no `parse()`
- `TillPolicy` - Plain function `decide(StoreOccupancy)` → one till more (more queuing than tills open, or full with every till busy), one less (nobody queuing, ≥ 2 tills free) or none; within 1..8
- `OccupancyRepositorySPI` - The latest report per store, and each store's recent reports (methods: saveIfNewer - false if an as new or newer one is stored, findAll, appendToHistory - idempotent, drops the store's reports before `keepSince`, history(store, since))
- `CheckoutSystemSPI` - The stores' checkout systems, which own the tills (method: setTills - throws if refused or unreachable)
- `OccupancyHandler` - (methods: record - via StoreOccupancyReceiver, `saveIfNewer`, if stored `appendToHistory` (kept `RETENTION` = 30 min) and fires `OccupancyChanged`, not audit-logged; current; history(store) - the last `WINDOW` = 10 min, for the charts on /locations)
- `AutoTillsHandler` - Automatic tills (method: adjust(store) - via AutoTillsReceiver; the store's latest report → `TillPolicy` → `CheckoutSystemSPI.setTills`, audit `TILLS_OPENED` / `TILLS_CLOSED` / `TILLS_CHANGE_FAILED`; synchronized; a 10 s cooldown per store after a change, kept in memory)
- `OccupancyChanged` - CDI event (via `AsyncEvents`), not an `InventoryEvent`: observed by the live updates of `/locations` and by AutoTillsReceiver

### cross.events
- `AsyncEvents` - Fires core's events (`InventoryEvent`s, `OccupancyChanged`) with `Event.fireAsync` on the `@EventExecutor` instead of CDI's default executor (Quarkus' platform worker pool); the Handlers use it instead of `Event<…>`; ArchUnit forbids a direct `fireAsync` elsewhere (methods: fire(InventoryEvent), fire(OccupancyChanged))
- `EventExecutor` - Qualifier of the plain `java.util.concurrent.Executor` the events are delivered on; core only names it, `inbound-event` produces it (virtual threads)

### cross.reset
- `ResetRepositorySPI` - Deletes the stock of every location, every replenishment request, shipment and supplier order in one transaction; the id sequences stay (method: deleteAll)
- `ResetHandler` - Resets the demo, needed since Flyway keeps the data across restarts (method: reset - via AdminReceiver: deleteAll, `AuditLogSPI.clear`, audit `INVENTORY_RESET`, fires `InventoryReset`). Messages in flight are harmless: a late supplier delivery adds to the DC, a late shipment arrival finds no shipment and is ignored

**Responsibilities**:
- Implement business logic for each use case
- Coordinate between inbound adapters (Receivers) and outbound ports (SPIs)
- Log events to audit trail
- Invoke supplier services for orders
- Manage inventory updates

**Design Pattern**: Each Handler's public methods are its inbound port - there is no separate API interface; receivers inject the Handler class directly. A Handler uses one or more SPIs (outbound ports). Handlers depend on the SPI interface, not the concrete implementation - implementations are injected at runtime from the relevant `outbound-*` module.

---

## outbound-postgres

**Purpose**: PostgreSQL persistence adapter - implements InventoryRepositorySPI, ReplenishmentRepositorySPI, SupplierOrderRepositorySPI and ResetRepositorySPI
**Package**: `org.svenehrke.triptychdemo.cross.inventory`, `org.svenehrke.triptychdemo.cross.replenishment`, `org.svenehrke.triptychdemo.cross.purchasing`, `org.svenehrke.triptychdemo.cross.reset`, `org.svenehrke.triptychdemo.cross.jdbc`

### Services
- `InventoryService` - Implements InventoryRepositorySPI with plain SQL (via `StockTable`)
  - One `stock` row per location and product
  - Handles stock additions and deductions per location, demand recording and the period close (calls core's pure `DemandEstimate`/`LearnedLevels`)
- `StockTable` / `StockRow` - SQL of the `stock` table and its row record (unique on locationId + name + type; plus periodDemand, avgDemand, demandVar, minLevel, maxLevel - at the DC, too); every write returns the row afterwards (`... RETURNING`)
- `ReplenishmentService` - Implements ReplenishmentRepositorySPI: stock transfer (DC −qty, request update, IN_TRANSIT shipment row) in one transaction; locks the DC stock row first, then requests, then the target row (created if missing, so it shows what is in transit); `receiveShipment` locks the shipment, then the target row, adds the quantity and marks it ARRIVED; `requestIfLow` locks the DC row, then the target row, sums in transit + outstanding requests and stores an AUTOMATIC request if below min (unserved); `allocate` shares the DC stock via `FairShare`; every request created adds its quantity to the DC row's periodDemand
- `ReplenishmentRequestTable` - SQL of the `replenishment_request` table; rows map straight to core's `ReplenishmentRequest`
- `ShipmentTable` / `ShipmentRow` - SQL of the `shipment` table and its row record (requestId, locationId, productName, type, quantity, status, dispatchedAt, arrivedAt; core's `Shipment` has no type)
- `SupplierOrderService` - Implements SupplierOrderRepositorySPI; locks the DC stock row first, then the supplier orders
- `SupplierOrderTable` - SQL of the `supplier_order` table; rows map straight to core's `SupplierOrder`
- `OccupancyService` - Implements OccupancyRepositorySPI (via `StoreOccupancyTable` and `StoreOccupancyHistoryTable`)
- `StoreOccupancyTable` - SQL of the `store_occupancy` table (one row per store); `upsertIfNewer` is one `insert … on conflict … do update … where excluded.measuredAt > store_occupancy.measuredAt`, so no lock is needed; not deleted by the reset
- `StoreOccupancyHistoryTable` - SQL of the `store_occupancy_history` table (every report per store, same columns, PK `(storeId, measuredAt)`): `insertIfAbsent` (`on conflict do nothing`), `deleteBefore(store, before)`, `findSince(store, since)`; not deleted by the reset
- `PurchaseService` - Implements PurchaseRepositorySPI (via `PurchaseTable`; `append` is one transaction: insert, then delete the old rows)
- `PurchaseTable` - SQL of the `purchase` table (id identity, locationId, products, units, purchasedAt): `insert`, `deleteBefore(location, before)`, `findRecent(location, limit)`; deleted by the reset
- `ResetService` - Implements ResetRepositorySPI: bulk delete of the five tables (stock, requests, shipments, supplier orders, purchases) in one transaction; identity columns keep counting, so a new row never reuses an id a message in flight still refers to

- `Db` - small JDBC helper (`query`, `queryOne`, `queryInt`, `update`, `insert`) on the Agroal datasource; inside `@Transactional` every call uses the transaction's connection

**Technology**: Plain SQL over JDBC (Agroal datasource, Narayana JTA for `@Transactional`), PostgreSQL, Flyway - no ORM
**Schema**: Flyway migrations in `src/main/resources/db/migration` (`V1__initial_schema.sql`, `V2__identity_ids.sql`: identity ids instead of the Panache sequences, `V3__seed_origin.sql`: supplier order origin `SEED`, `V4__store_occupancy.sql`, `V5__store_occupancy_capacity.sql`, `V6__store_occupancy_history.sql`, `V7__purchase.sql`), applied at startup - every schema change needs a new `V<n>__*.sql`; the flow tests run every query against the migrated schema
**Database**: `stock`, `replenishment_request`, `shipment`, `supplier_order`, `store_occupancy`, `store_occupancy_history` and `purchase` tables in PostgreSQL
**Transactional**: Yes (@Transactional on write operations)

---

## outbound-mongodb

**Purpose**: MongoDB persistence adapter - implements AuditLogSPI
**Package**: `org.svenehrke.triptychdemo.cross.auditlog`

### Services
- `AuditLogService` - Implements AuditLogSPI using Panache MongoDB (`clear` deletes every entry)
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
**Package**: `org.svenehrke.triptychdemo.feature.<commodity>` (fruit, dairy, vegetable); `cross.occupancy` for the checkout systems

### Services (by product category)
- `FruitSupplierService` - Implements FruitSupplierSPI using REST client
- `VegetablesSupplierService` - Implements VegetablesSupplierSPI using REST client
- `DairySupplierService` - Implements DairySupplierSPI using REST client
- `CheckoutSystemService` - Implements CheckoutSystemSPI: `PUT /cashpoint-stub/stores/{id}/tills` (`checkout-system` client)

### REST Clients (auto-generated from service interfaces)
- `FruitSupplierClient` - REST client proxy for fruit supplier
- `VegetablesSupplierClient` - REST client proxy for vegetable supplier
- `DairySupplierClient` - REST client proxy for dairy supplier
- `CheckoutSystemClient` - REST client proxy for the stores' checkout systems (tills)

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
**Package**: `org.svenehrke.triptychdemo.feature.nonfood` (named even though it's the only feature in this module, so the `TriptychArchitecture` slices rule (checked by `ArchitectureTest` in `app-server`) stays fully automatic - see `concepts.md`); `org.svenehrke.triptychdemo.cross.replenishment` for the carrier

### Services
- `NonFoodSupplierService` - Implements NonFoodSupplierSPI using Kafka emitter
  - Publishes order messages to `nonfood-orders` topic
  - Uses SmallRye Reactive Messaging Emitter
- `CarrierService` (`cross.replenishment`) - Implements CarrierSPI: publishes a `ShipmentMessage` (shipmentId, locationId, productName, quantity) to the `shipments` topic (channel `shipments-out`), one topic for all commodities

**Technology**: Quarkus SmallRye Reactive Messaging, Kafka Emitter
**Topics**: `nonfood-orders` (configured in application.properties as `nonfood-orders-out` channel), `shipments` (`shipments-out`)
**Pattern**: One-way async messaging (fire-and-forget)

---

## external-outbound-rest

**Purpose**: Mock REST supplier stubs - simulates external REST APIs
**Package**: `org.svenehrke.triptychdemo.external.outbound.rest` (unchanged - external-* modules are not part of the hexagonal architecture, see `concepts.md`, and were not touched by the 2026-09-13 feature/cross restructuring)

### Supplier Stubs
- `FruitSupplierStub` - Mock REST endpoint for fruit supplier
- `VegetablesSupplierStub` - Mock REST endpoint for vegetable supplier
- `DairySupplierStub` - Mock REST endpoint for dairy supplier
- `LeadTime` - delays a stub's delivery (one copy per external-outbound module)

**Responsibilities**:
- Receive order requests via REST (called by outbound-httpclient services)
- Publish delivery notifications to Kafka topics (`fruit-deliveries`, `vegetables-deliveries`, `dairy-deliveries`) after the supplier lead time (`LeadTime`: `supplier-stub.lead-time`, 30s ± 20% in dev, 0 in `%test` and e2e; scheduled on Quarkus' worker pool, so the receiving thread returns right away)
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
- `LeadTime` - delays a stub's delivery

**Responsibilities**:
- Receive order requests via SOAP (called by outbound-webservice services)
- Publish delivery notifications to Kafka topics (`beverages-deliveries`, `meat-deliveries`, `bakery-deliveries`) after the supplier lead time (`LeadTime`: `supplier-stub.lead-time`, 30s ± 20% in dev, 0 in `%test` and e2e; scheduled on Quarkus' worker pool, so the receiving thread returns right away)
- Simulate supplier behavior

**Technology**: Apache CXF SOAP endpoint, Quarkus SmallRye Reactive Messaging Emitter
**In Dev/Test**: Runs in same Quarkus instance as main application
**WSDL Path**: `/soap/[beverage|meat|bakery]-supplier`
**Integration**: Completes the SOAP → Kafka cycle for product deliveries

---

## external-outbound-kafka

**Purpose**: Mock Kafka supplier stub - simulates external Kafka-based order processor
**Package**: `org.svenehrke.triptychdemo.external.outbound.kafka.nonfood` (unchanged, see note above); `…kafka.carrier` for the carrier

### Supplier Stubs
- `NonFoodSupplierStub` - Mock Kafka consumer/producer for non-food supplier
  - Consumes from `nonfood-orders` topic
  - Publishes to `nonfood-deliveries` topic
- `CarrierStub` (`carrier`) - Mock carrier between the DC and the locations
  - Consumes from `shipments` topic
  - Publishes `ShipmentArrivalMessage(shipmentId)` to `shipment-arrivals` after `carrier-stub.transit-time` (20s ± 20% in dev, 0 in `%test` and e2e)
- `LeadTime` - delays a stub's delivery (`later(delay, action)` also serves the carrier's transit time)

**Responsibilities**:
- Listen to order events from `nonfood-orders` topic
- Process orders asynchronously
- Publish delivery notifications to `nonfood-deliveries` topic after the supplier lead time (`LeadTime`: `supplier-stub.lead-time`, 30s ± 20% in dev, 0 in `%test` and e2e; scheduled on Quarkus' worker pool, so the receiving thread returns right away)
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
- `CashpointStub` - Mock checkout systems of the physical stores: `@Scheduled(every = "${cashpoint-stub.tick}")` (100 ms, `off` in `%test` and e2e) advances one `StoreSimulation` per store and sends one `PurchaseRequest` (with `storeId`) per paying customer, from the store's stock; logs occupancy, till queue, entered/paid customers every 10 s; every 5 s per store (and right after a till change) an `OccupancyMessage` on `store-occupancy` (key = storeId); till changes from `CashpointTillsStub` are queued and applied by the next tick
- `StoreSimulation` - Plain class, one store's customers: arrivals along a rush-hour curve (0.3-2.5 × what the configured tills serve), 30-60 real minutes of shopping at 1 day = 1 min, a till queue; the tills (`till-time` 15 s ± 20 %, in demo time) cap the purchases; a full store (`capacity`) turns new customers away (counted per demo day). `setTills` opens/closes tills (a busy till closes once its customer has paid); the arrivals stay scaled to the configured tills, so more tills let more of them in - fewer turned away, more sales. Unit-tested in `StoreSimulationTest`
- `CashpointTillsStub` - `PUT /cashpoint-stub/stores/{storeId}/tills` `{"tills": n}`: 204; 400 outside 1..8; 404 unknown store. Called by the app's `CheckoutSystemService`
- `CashpointStubConfig` - `@ConfigMapping(prefix = "cashpoint-stub")`: tick, day, till-time, per store its capacity and tills (Zurich 16/4, Basel 10/2, Bern 6/1)
- `ProductsApiClient` - REST client for `GET /api/locations/{id}/products`

**Responsibilities**:
- Simulate external systems publishing events
- In test scenarios: trigger purchase events via `cashpoint-purchases` topic
- In demo: can be used to simulate real-world purchase patterns

**Technology**: Kafka producer (for test scenarios), REST client (for queries)

---

## Summary by Layer

### Presentation Layer (HTTP Inbound)
- `inbound-http-html` module - HTML user interfaces (AdminReceiver, ShopReceiver, LocationReceiver, InventoryEventsReceiver)
- `inbound-http-jsonapi` module - JSON REST API (ProductApiReceiver, LocationApiReceiver)
- `inbound-event` module - CDI async domain events from core and timers (DeliveryEventReceiver, AutoReplenishmentReceiver, AutoPurchasingReceiver, ShipmentCatchUpReceiver, DemandPeriodReceiver, AutoTillsReceiver)
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
- `outbound-postgres` module - InventoryService (StockEntity), ReplenishmentService (ReplenishmentRequestEntity, ShipmentEntity), SupplierOrderService (SupplierOrderEntity), packages `cross.inventory`, `cross.replenishment`, `cross.purchasing`
- `outbound-mongodb` module - AuditLogService (AuditLogEntryEntity), package `cross.auditlog`
- Responsibility: Persist and query data

### Supplier Integration Layer (Outbound Adapters)
- `outbound-httpclient` - REST suppliers (Fruits, Vegetables, Dairy), each in its own `feature.<commodity>` package
- `outbound-webservice` - SOAP suppliers (Beverages, Meat, Bakery), each in its own `feature.<commodity>` package
- `outbound-kafka` - Kafka supplier (NonFood), package `feature.nonfood`; carrier (CarrierService), package `cross.replenishment`
- Responsibility: Call external supplier systems

### Mock External Systems Layer
- `external-outbound-rest` - Mock REST suppliers
- `external-outbound-soap` - Mock SOAP suppliers
- `external-outbound-kafka` - Mock Kafka supplier, mock carrier
- `external-inbound-kafka` - Mock event sources
- Responsibility: Simulate external system behavior via Kafka integration
- Package root: `org.svenehrke.triptychdemo.external.*` for all four - untouched by the feature/cross restructuring, since these modules sit outside the hexagonal architecture entirely (see `concepts.md`)

---

## Participant Count by Module

| Module | Participants | Type |
|--------|--------------|------|
| inbound-http-html | 4 | HTTP HTML Receivers |
| inbound-http-jsonapi | 3 | HTTP JSON API Receivers + request records |
| inbound-kafka | 10 + 7 | Kafka Receivers + cashpoint / shipment-arrival / occupancy message types |
| inbound-event | 7 + 1 | CDI event / scheduled Receivers + event executor producer |
| core | 17 Handlers, 13 SPI interfaces, 36 domain records/enum/sealed interfaces/events (+ `TillPolicy`) | Feature (7 packages) + Cross (11 packages) |
| outbound-postgres | 7 | Services (InventoryService, ReplenishmentService, SupplierOrderService) + Entities (StockEntity, ReplenishmentRequestEntity, ShipmentEntity, SupplierOrderEntity) |
| outbound-mongodb | 2 | Service (AuditLogService) + Entity (AuditLogEntryEntity) |
| outbound-httpclient | 4 | Services + 4 REST Clients |
| outbound-webservice | 3 | Services + 3 SOAP Clients |
| outbound-kafka | 2 | Services (NonFoodSupplierService, CarrierService) |
| external-outbound-rest | 3 | Supplier Stubs |
| external-outbound-soap | 3 | Supplier Stubs |
| external-outbound-kafka | 2 | Supplier Stub, Carrier Stub |
| external-inbound-kafka | 3 | Mock Event Sources + till endpoint |
| **Total** | **~115 classes** | **across 15 Maven modules (child modules of the root POM, app-server included)** |

---

**For instructions on adding a new commodity, adding a new adapter technology, or otherwise keeping this file in sync with code changes**, see `../ai/maintaining-module-participants.md`.
