# Architecture Flow Analysis

Flow analysis of the supermarket inventory system, starting from all Receiver classes and tracing through to their endpoints.

## Important: Original vs. Indirect Flows

**This document shows only flows with an original external source** (HTTP requests from users, form submissions, or API calls). It does NOT include flows triggered indirectly by other events (like Kafka delivery topics).

**Why?** All Kafka inbound delivery flows originate from HTTP order flows and are the response paths—documenting them separately would be redundant. The tree diagrams below show the complete end-to-end paths including where Kafka topics fit in the flow.

**For technical reference on Kafka topic cycles and integrations**, see `architecture-flow-kafka-reference.md`.

## Key Insight: Kafka Cycles

The system uses **Kafka as the integration point between internal ordering logic and external supplier stubs**. When an order is placed, it triggers a cycle:

1. HTTP request → Handler → Supplier Service → External Supplier Stub
2. Supplier Stub sends delivery notification → **Kafka topic**
3. Kafka Inbound Receiver → InventoryHandler → PostgreSQL inventory update

This creates bidirectional flows through Kafka topics, connecting request/response cycles that would otherwise be synchronous into event-driven asynchronous flows.

## HTTP Inbound Flows

### AdminReceiver (/admin) - HTML Forms → REST/SOAP/Kafka → Kafka Delivery Topics

`/` is a static landing page (`META-INF/resources/index.html` in inbound-http-html) with one link per audience:
`/admin`, `/locations` and `/shop`. The pages themselves have no nav and don't link to each other.

The HTML receivers don't render HTML: `GET /admin`, `GET /locations` and `GET /shop` return a static page shell, and every
view endpoint returns a JSON envelope `{route, vm}` (`UiResponse`) that the browser renders with the
hono/html templates in `hx-hono.js` (see `docs/architecture/browser-templating_wip.md`).

#### GET /admin - Admin Dashboard
```
AdminReceiver.shell()  → static shell (shells/admin.html), whose #app loads GET /admin/page
AdminReceiver.page()   → UiResponse(AdminPage, {locations, products, pendingRequests, auditEntries})
├─ ProductsHandler.listAllLocations()  (as in GET /admin/inventory-fragment)
├─ ReplenishmentHandler.listPending()  (as in GET /admin/requests-fragment)
└─ AuditLogHandler.recent(limit)  (as in GET /admin/audit-fragment)
```

#### GET /admin/inventory-fragment - Inventory Update
Fetched by `/admin` on every `inventoryChanged` event from `GET /inventory/events`; the browser morphs the rendered
product × location matrix (`hx-swap="innerMorph"`) into `#admin-inventory`, so new products appear and quantities
typed into a row's *Restock* form survive. Columns are the locations, DC first (`Locations.ALL`). Each row's
*Restock* form posts to the `POST /admin/order-*` endpoint of its product type's supplier, so it restocks the DC.
```
AdminReceiver.inventoryFragment()   → UiResponse(AdminInventory, {locations, products})
└─ ProductsHandler.listAllLocations()   (one ProductStock per product, with its stock per location)
   └─ InventoryRepositorySPI.findAllLocations()
      └─ InventoryService (outbound-postgres)
         └─ PostgreSQL (StockEntity.listAll(), table stock)
   └─ Sorted by name (case-insensitive), then type; sold-out products stay listed
```

#### GET /admin/requests-fragment - Pending Requests
Fetched by `/admin` on every `inventoryChanged` event, morphed into `#admin-requests`.
```
AdminReceiver.requestsFragment()   → UiResponse(AdminRequests, {requests})
└─ ReplenishmentHandler.listPending()
   └─ ReplenishmentRepositorySPI.findPending()   (oldest first)
      └─ ReplenishmentService (outbound-postgres)
         └─ PostgreSQL (table replenishment_request)
```

#### POST /admin/requests/{id}/fulfil, /reject - Head Office Decides on a Pending Request
```
AdminReceiver.fulfilRequest(id) / rejectRequest(id)
├─ AuditLogHandler.log("FULFIL_RECEIVED" / "REJECT_RECEIVED")
└─ ReplenishmentHandler.fulfil(id) / reject(id)
   ├─ ReplenishmentRepositorySPI.fulfil(id) / reject(id)   (one transaction)
   │  └─ ReplenishmentService (outbound-postgres)
   │     └─ PostgreSQL: lock DC stock row, then the request (FOR UPDATE);
   │        fulfil moves what the DC has (ahead of older requests), the rest stays PENDING;
   │        reject cancels what is outstanding
   ├─ AuditLogSPI.log("STOCK_TRANSFERRED" / "REQUEST_PENDING" / "REQUEST_CANCELLED")
   └─ Event<InventoryEvent>.fireAsync(ReplenishmentChanged(location))
   → 200 empty body, or 409 UiResponse(OrderErrors) if the request is no longer pending
```

#### GET /admin/audit-fragment - Audit Log Update
```
AdminReceiver.auditFragment()
└─ AuditLogHandler.recent(limit)
   └─ AuditLogSPI.findRecent()
      └─ AuditLogService (outbound-mongodb)
         └─ MongoDB (audit_log collection, AuditLogEntryEntity)
```

#### POST /admin/order-fruits - HTML Form → REST Client → Kafka Delivery Topic
```
AdminReceiver.orderFruits()
├─ AuditLogHandler.log("FRUITS_ORDER_RECEIVED")
└─ FruitsHandler.order(productName, quantity)
   ├─ AuditLogSPI.log("FRUITS_ORDER_PROCESSING")
   │  └─ AuditLogService (outbound-mongodb)
   │     └─ MongoDB
   ├─ FruitSupplierSPI.placeOrder()
   │  └─ FruitSupplierService (outbound-httpclient)
   │     └─ FruitSupplierClient (REST)
   │        └─ FruitSupplierStub (external-outbound-rest, same Quarkus instance)
   │           └─ Emitter → fruit-deliveries-out channel
   │              └─ Kafka Topic: fruit-deliveries
   │                 └─ FruitDeliveryReceiver (@Incoming("fruit-deliveries"))
   │                    ├─ AuditLogHandler.log("FRUIT_DELIVERY_RECEIVED")
   │                    └─ InventoryHandler.updateFruitAmount()
   │                       ├─ InventoryRepositorySPI.addAmount(DC, …)   (every delivery goes to the DC)
   │                       │  └─ InventoryService (outbound-postgres)
   │                       │     └─ PostgreSQL
   │                       ├─ AuditLogSPI.log("FRUIT_INVENTORY_UPDATED")
   │                       └─ Event<InventoryEvent>.fireAsync(DeliveredToDc)   (after the commit; decoupled from the delivery;
   │                          │                                                also refreshes the pages, see GET /inventory/events)
   │                          └─ DeliveryEventReceiver (inbound-event, @ObservesAsync)
   │                             ├─ AuditLogHandler.log("DELIVERED_TO_DC_RECEIVED")
   │                             └─ ReplenishmentHandler.fulfilPending(productName)
   │                                └─ ReplenishmentRepositorySPI.allocate()   (shared among all pending requests, FairShare)
   │                                   └─ ReplenishmentService (outbound-postgres) → PostgreSQL
   └─ AuditLogSPI.log("FRUITS_ORDER_PLACED")
      └─ AuditLogService (outbound-mongodb)
         └─ MongoDB
```

#### POST /admin/order-vegetables - HTML Form → REST Client → Kafka Delivery Topic
```
AdminReceiver.orderVegetables()
├─ AuditLogHandler.log("VEGETABLES_ORDER_RECEIVED")
└─ VegetablesHandler.order()
   ├─ AuditLogSPI.log("VEGETABLES_ORDER_PROCESSING")
   ├─ VegetablesSupplierService (outbound-httpclient)
   │  └─ REST → VegetablesSupplierStub
   │     └─ Kafka: vegetables-deliveries-out
   │        └─ Topic: vegetables-deliveries
   │           └─ VegetablesDeliveryReceiver
   │              └─ InventoryHandler.update()
   │                 └─ InventoryService (PostgreSQL)
   └─ AuditLogSPI.log("VEGETABLES_ORDER_PLACED")
```

#### POST /admin/order-dairy - HTML Form → REST Client → Kafka Delivery Topic
```
AdminReceiver.orderDairy()
├─ AuditLogHandler.log("DAIRY_ORDER_RECEIVED")
└─ DairyHandler.order()
   ├─ AuditLogSPI.log("DAIRY_ORDER_PROCESSING")
   ├─ DairySupplierService (outbound-httpclient)
   │  └─ REST → DairySupplierStub
   │     └─ Kafka: dairy-deliveries-out
   │        └─ Topic: dairy-deliveries
   │           └─ DairyDeliveryReceiver
   │              └─ InventoryHandler.update()
   │                 └─ InventoryService (PostgreSQL)
   └─ AuditLogSPI.log("DAIRY_ORDER_PLACED")
```

#### POST /admin/order-beverages - HTML Form → SOAP Client → Kafka Delivery Topic
```
AdminReceiver.orderBeverages()
├─ AuditLogHandler.log("BEVERAGES_ORDER_RECEIVED")
└─ BeveragesHandler.order()
   ├─ AuditLogSPI.log("BEVERAGES_ORDER_PROCESSING")
   ├─ BeverageSupplierService (outbound-webservice)
   │  └─ SOAP → BeverageSupplierStub
   │     └─ Kafka: beverages-deliveries-out
   │        └─ Topic: beverages-deliveries
   │           └─ BeveragesDeliveryReceiver
   │              └─ InventoryHandler.update()
   │                 └─ InventoryService (PostgreSQL)
   └─ AuditLogSPI.log("BEVERAGES_ORDER_PLACED")
```

#### POST /admin/order-meat - HTML Form → SOAP Client → Kafka Delivery Topic
```
AdminReceiver.orderMeat()
├─ AuditLogHandler.log("MEAT_ORDER_RECEIVED")
└─ MeatHandler.order()
   ├─ AuditLogSPI.log("MEAT_ORDER_PROCESSING")
   ├─ MeatSupplierService (outbound-webservice)
   │  └─ SOAP → MeatSupplierStub
   │     └─ Kafka: meat-deliveries-out
   │        └─ Topic: meat-deliveries
   │           └─ MeatDeliveryReceiver
   │              └─ InventoryHandler.update()
   │                 └─ InventoryService (PostgreSQL)
   └─ AuditLogSPI.log("MEAT_ORDER_PLACED")
```

#### POST /admin/order-bakery - HTML Form → SOAP Client → Kafka Delivery Topic
```
AdminReceiver.orderBakery()
├─ AuditLogHandler.log("BAKERY_ORDER_RECEIVED")
└─ BakeryHandler.order()
   ├─ AuditLogSPI.log("BAKERY_ORDER_PROCESSING")
   ├─ BakerySupplierService (outbound-webservice)
   │  └─ SOAP → BakerySupplierStub
   │     └─ Kafka: bakery-deliveries-out
   │        └─ Topic: bakery-deliveries
   │           └─ BakeryDeliveryReceiver
   │              └─ InventoryHandler.update()
   │                 └─ InventoryService (PostgreSQL)
   └─ AuditLogSPI.log("BAKERY_ORDER_PLACED")
```

#### POST /admin/order-nonfood - HTML Form → Kafka Order Topic → Kafka Delivery Topic
```
AdminReceiver.orderNonFood()
├─ AuditLogHandler.log("NONFOOD_ORDER_RECEIVED")
└─ NonFoodHandler.order()
   ├─ AuditLogSPI.log("NONFOOD_ORDER_PROCESSING")
   ├─ NonFoodSupplierService (outbound-kafka)
   │  └─ Emitter → nonfood-orders-out channel
   │     └─ Topic: nonfood-orders
   │        └─ NonFoodSupplierStub (@Incoming("nonfood-orders"))
   │           (external-outbound-kafka - reads orders from Kafka)
   │           └─ Emitter → nonfood-deliveries-out channel
   │              └─ Topic: nonfood-deliveries
   │                 └─ NonFoodDeliveryReceiver
   │                    └─ InventoryHandler.update()
   │                       └─ InventoryService (PostgreSQL)
   └─ AuditLogSPI.log("NON_FOOD_ORDER_PLACED")
```

### ShopReceiver (/shop) - HTML Forms → PostgreSQL/MongoDB

#### GET /shop - Shop Catalog
```
ShopReceiver.shell()  → static shell (shells/shop.html), whose #app loads GET /shop/page
ShopReceiver.page()   → UiResponse(ShopPage, {products, errors})
└─ ProductsHandler.listAll(ONLINE)   (the shop sells the online FC's stock)
   └─ InventoryRepositorySPI.findAll(ONLINE)
      └─ InventoryService (outbound-postgres)
         └─ PostgreSQL
   └─ Filter in-stock products (availableAmount > 0), sorted by name
```

#### GET /shop/inventory-fragment - Inventory Fragment
Fetched by `/shop` on every `inventoryChanged` event from `GET /inventory/events`; the browser morphs the rendered
products section (`hx-swap="innerMorph"`) into `#shop-products`, so new products appear, sold-out ones
disappear, and typed quantities survive.
```
ShopReceiver.inventoryFragment()   → UiResponse(ShopProducts, {products})
└─ ProductsHandler.listAll(ONLINE)
   └─ InventoryRepositorySPI.findAll(ONLINE)
      └─ InventoryService (outbound-postgres)
         └─ PostgreSQL
   └─ Filter in-stock products (availableAmount > 0), sorted by name
```

#### GET /inventory/events - Inventory Change Stream (SSE)
Served by `InventoryEventsReceiver` (`/inventory`) and opened once by the shop, admin and locations shells
(`hx-sse:connect`, outside `#app`, so a re-render of the page keeps it). Replaces the former 3 s polling: each event
makes `/shop` re-fetch `GET /shop/inventory-fragment`, `/admin` re-fetch `GET /admin/inventory-fragment` and
`GET /admin/requests-fragment`, and each section of `/locations` re-fetches `GET /locations/{id}/inventory-fragment`. The event
carries no location: every page refreshes on every change (the core events do carry one, for filtering later).
```
InventoryEventsReceiver.events()   → text/event-stream, never ends
├─ event: inventoryChanged   (once on (re)connect, so nothing missed while disconnected)
├─ InventoryEventBroadcaster.events()   (inbound-http-html, JDK Flow.Publisher fed by @ObservesAsync InventoryEvent)
│  └─ event: inventoryChanged per InventoryEvent, fired by core with fireAsync after a committed change:
│     ├─ DeliveredToDc          ← InventoryHandler.update*Amount()   (every Kafka delivery)
│     ├─ StockDeducted          ← PurchaseHandler.deduct()           (shop/JSON API checkout, cashpoint sale; only if something was deducted)
│     ├─ ReplenishmentChanged   ← ReplenishmentHandler               (every request, fulfil, reject, automatic request; fulfilPending per location served)
│     └─ LevelsRecalculated     ← ReorderPolicyHandler.closePeriod() (end of every demand period)
└─ ": heartbeat" comment every 15 s
```

#### POST /shop/checkout - Customer Purchase
```
ShopReceiver.checkout(productNames[], quantities[])
├─ AuditLogHandler.log("PURCHASE_RECEIVED")
└─ PurchaseHandler.checkout(purchase)
   ├─ AuditLogSPI.log("PURCHASE_PROCESSING")
   │  └─ AuditLogService (outbound-mongodb)
   │     └─ MongoDB
   ├─ InventoryRepositorySPI.deductAll(ONLINE, quantities, REJECT)  (one transaction, all-or-nothing)
   │  └─ InventoryService (outbound-postgres)
   │     └─ PostgreSQL (SELECT ... FOR UPDATE per product, sorted by name)
   ├─ InventoryRepositorySPI.recordDemand(ONLINE, quantities)   (own transaction: a rejected checkout is demand, too)
   ├─ Event<InventoryEvent>.fireAsync(StockDeducted)   (if something was deducted; see Automatic Replenishment below)
   └─ Completed: AuditLogSPI.log("INVENTORY_DEDUCTED") → 200 UiResponse(ShopPage), fresh page
      Rejected:  AuditLogSPI.log("PURCHASE_REJECTED") → 409 UiResponse(ShopPage) with shortage messages, nothing deducted
         └─ AuditLogService (outbound-mongodb)
            └─ MongoDB
```

### LocationReceiver (/locations) - Stores / Online FC Page → PostgreSQL

One page with a section per store and the online FC, in `Locations.REPLENISHED` order (`#location-{id}`, so
`/locations#bern` jumps to Bern); the DC (managed on `/admin`) and unknown ids are 404 on the `{id}` endpoints.

#### GET /locations - Locations Page
```
LocationReceiver.shell()  → static shell (shells/locations.html), whose #app loads GET /locations/page
LocationReceiver.page()   → UiResponse(LocationsPage, {locations: [inventory per location]})
                            (ProductsHandler.listAllLocations() once, each inventory as in the fragment below)
```

#### GET /locations/{id}/inventory-fragment - Stock and Requests
Fetched per section on every `inventoryChanged` event and morphed into it; the ids inside carry the location id.
```
LocationReceiver.inventoryFragment(id)   → UiResponse(LocationInventory, {locationId, locationName, products, requests})
├─ ProductsHandler.listAllLocations()   (every product the DC carries: stock here + at the DC)
│  └─ InventoryRepositorySPI.findAllLocations()
│     └─ InventoryService (outbound-postgres) → PostgreSQL
└─ ReplenishmentHandler.listRecent(location, 20)   (newest first)
   └─ ReplenishmentRepositorySPI.findRecent()
      └─ ReplenishmentService (outbound-postgres) → PostgreSQL
```

#### POST /locations/{id}/requests - Request Stock from the DC
```
LocationReceiver.request(id, productName, quantity)
├─ AuditLogHandler.log("REQUEST_RECEIVED")
├─ StockRequest.parse(location, productName, quantity)   → 400 UiResponse(OrderErrors) if invalid
└─ ReplenishmentHandler.request(stockRequest)
   ├─ AuditLogSPI.log("REQUEST_PROCESSING")
   ├─ ReplenishmentRepositorySPI.request()   (one transaction)
   │  └─ ReplenishmentService (outbound-postgres)
   │     └─ PostgreSQL: lock DC stock row; store the request; share what the DC has among all pending requests
   │        of the product in proportion to what each still needs (FairShare) - the rest stays PENDING
   ├─ AuditLogSPI.log("STOCK_TRANSFERRED" / "REQUEST_PENDING")
   └─ Event<InventoryEvent>.fireAsync(ReplenishmentChanged(location))
   → 200 empty body; 409 UiResponse(OrderErrors) if the DC never carried the product ("REQUEST_REJECTED")
```

### ProductApiReceiver (/api/products) - JSON API → REST/SOAP/Kafka → Kafka Delivery Topics

#### GET /api/products - Product List (JSON)
```
ProductApiReceiver.list()
└─ ProductsHandler.listAll(ONLINE)   (the JSON API is the online shop's)
   └─ InventoryRepositorySPI.findAll(ONLINE)
      └─ InventoryService (outbound-postgres)
         └─ PostgreSQL
```

#### POST /api/products/order-* (Fruits, Vegetables, Dairy, Beverages, Meat, Bakery, NonFood)

Same Kafka cycle flows as admin endpoints above. The order flows through the same handlers and supplier services, connecting to Kafka delivery topics:

**Common to all order endpoints (REST API and HTML):**
- **Fruits, Vegetables, Dairy** → REST Supplier Stubs → publish to Kafka delivery topics
- **Beverages, Meat, Bakery** → SOAP Supplier Stubs → publish to Kafka delivery topics  
- **NonFood** → Kafka order topic → Stub consumes → publishes to Kafka delivery topic

Example (REST order flow):
```
ProductApiReceiver.orderFruits(request)
├─ AuditLogHandler.log("FRUITS_ORDER_RECEIVED")
└─ FruitsHandler.order()
   ├─ AuditLogSPI.log("FRUITS_ORDER_PROCESSING")
   ├─ FruitSupplierService (outbound-httpclient)
   │  └─ REST Stub
   │     └─ Kafka: fruit-deliveries topic
   │        └─ FruitDeliveryReceiver
   │           └─ Inventory updated (PostgreSQL)
   └─ AuditLogSPI.log("FRUITS_ORDER_PLACED")
```

#### POST /api/products/purchase - Purchase Request (JSON)
```
ProductApiReceiver.purchase(request)
├─ AuditLogHandler.log("PURCHASE_RECEIVED")
└─ PurchaseHandler.checkout(purchase)
   ├─ AuditLogSPI.log("PURCHASE_PROCESSING")
   │  └─ AuditLogService (outbound-mongodb)
   │     └─ MongoDB
   ├─ InventoryRepositorySPI.deductAll(ONLINE, quantities, REJECT)  (one transaction, all-or-nothing)
   │  └─ InventoryService (outbound-postgres)
   │     └─ PostgreSQL (SELECT ... FOR UPDATE per product, sorted by name)
   ├─ InventoryRepositorySPI.recordDemand(ONLINE, quantities)   (own transaction: a rejected checkout is demand, too)
   ├─ Event<InventoryEvent>.fireAsync(StockDeducted)   (if something was deducted; see Automatic Replenishment below)
   └─ Completed: AuditLogSPI.log("INVENTORY_DEDUCTED") → 204
      Rejected:  AuditLogSPI.log("PURCHASE_REJECTED") → 409 with shortage messages, nothing deducted
         └─ AuditLogService (outbound-mongodb)
            └─ MongoDB
```

### LocationApiReceiver (/api/locations) - JSON API → PostgreSQL

#### GET /api/locations/{id}/products - Stock of One Location (JSON)
Read by the cashpoint stub to pick what a store sells. Any location incl. the DC; unknown id → 404.
```
LocationApiReceiver.list(id)
└─ ProductsHandler.listAll(location)
   └─ InventoryRepositorySPI.findAll(location)
      └─ InventoryService (outbound-postgres)
         └─ PostgreSQL
```

## Event and Timer Inbound Flows (inbound-event)

Automatic replenishment of the stores and the online FC (`split-inventory` phase 2). Both switches are off in `%test`
and in the e2e dev server (`inventory.demand-period=off`, `inventory.auto-replenishment.enabled=false`).

#### Timer: end of a demand period (`inventory.demand-period`, 1 min)
```
DemandPeriodReceiver.closePeriod()   (@Scheduled)
└─ ReorderPolicyHandler.closePeriod()
   ├─ InventoryRepositorySPI.closePeriod()
   │  └─ InventoryService (outbound-postgres)
   │     ├─ every store / online FC gets a row for every DC product (available 0, cold-start estimate)
   │     └─ per row, one transaction (FOR UPDATE): DemandEstimate.next(periodDemand) → LearnedLevels.of() → periodDemand = 0
   ├─ AuditLogSPI.log("PERIOD_CLOSED")
   └─ Event<InventoryEvent>.fireAsync(LevelsRecalculated)
      └─ AutoReplenishmentReceiver.onLevelsRecalculated (@ObservesAsync)
         └─ ReplenishmentHandler.replenishAllIfLow(DC products)
            └─ per product: requestIfLow for every store and the online FC, then one allocate (as below),
               so a DC shortfall is shared among all of them
```

#### Event: StockDeducted (after a checkout or a cashpoint sale)
```
AutoReplenishmentReceiver.onStockDeducted (@ObservesAsync)
└─ ReplenishmentHandler.replenishIfLow(location, productNames)
   ├─ ReplenishmentRepositorySPI.requestIfLow(location, productName)   per product, one transaction
   │  └─ ReplenishmentService (outbound-postgres) → PostgreSQL
   │     (DC row, then location row FOR UPDATE; available + outstanding < min → AUTOMATIC request up to max,
   │      not served yet)
   ├─ ReplenishmentRepositorySPI.allocate(productName)   if a request was created: DC stock shared among all
   │                                                      pending requests (FairShare), one transaction
   ├─ AuditLogSPI.log("AUTO_REQUEST_CREATED"), ("STOCK_TRANSFERRED"), ("REQUEST_PENDING")
   └─ Event<InventoryEvent>.fireAsync(ReplenishmentChanged)   (if a request was created)
```

## Note: Kafka Delivery Receivers

The Kafka inbound delivery flows (FruitDeliveryReceiver, VegetablesDeliveryReceiver, etc.) are completions of the HTTP order cycles documented above. They are not independent flows—they are triggered by HTTP POST requests and form the response path through Kafka topics. 

See `architecture-flow-kafka-reference.md` for technical details on Kafka topic cycles, configuration references, and data persistence patterns.
