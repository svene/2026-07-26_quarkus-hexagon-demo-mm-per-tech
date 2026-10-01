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

The HTML receivers don't render HTML: `GET /admin` and `GET /shop` return a static page shell, and every
view endpoint returns a JSON envelope `{route, vm}` (`UiResponse`) that the browser renders with the
hono/html templates in `hx-hono.js` (see `docs/architecture/browser-templating_wip.md`).

#### GET /admin - Admin Dashboard
```
AdminReceiver.shell()  → static shell (shells/admin.html), whose #app loads GET /admin/page
AdminReceiver.page()   → UiResponse(AdminPage, {products, auditEntries})
├─ ProductsHandler.listAll()
   └─ InventoryRepositorySPI.findAll()
      └─ InventoryService (outbound-postgres)
         └─ PostgreSQL (ProductEntity.listAll())
└─ AuditLogHandler.recent(limit)  (as in GET /admin/audit-fragment)
```

#### GET /admin/inventory-fragment - Inventory Update
```
AdminReceiver.inventoryFragment()
└─ ProductsHandler.listAll()
   └─ InventoryRepositorySPI.findAll()
      └─ InventoryService (outbound-postgres)
         └─ PostgreSQL
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
   │                       ├─ InventoryRepositorySPI.addAmount()
   │                       │  └─ InventoryService (outbound-postgres)
   │                       │     └─ PostgreSQL
   │                       └─ AuditLogSPI.log("FRUIT_INVENTORY_UPDATED")
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
└─ ProductsHandler.listAll()
   └─ InventoryRepositorySPI.findAll()
      └─ InventoryService (outbound-postgres)
         └─ PostgreSQL
   └─ Filter in-stock products (availableAmount > 0), sorted by name
```

#### GET /shop/inventory-fragment - Inventory Fragment
Polled every 3 s by `/shop`; the browser morphs the rendered products section (`hx-swap="innerMorph"`)
into `#shop-products`, so new products appear, sold-out ones disappear, and typed quantities survive.
```
ShopReceiver.inventoryFragment()   → UiResponse(ShopProducts, {products})
└─ ProductsHandler.listAll()
   └─ InventoryRepositorySPI.findAll()
      └─ InventoryService (outbound-postgres)
         └─ PostgreSQL
   └─ Filter in-stock products (availableAmount > 0), sorted by name
```

#### POST /shop/checkout - Customer Purchase
```
ShopReceiver.checkout(productNames[], quantities[])
├─ AuditLogHandler.log("PURCHASE_RECEIVED")
└─ PurchaseHandler.checkout(purchase)
   ├─ AuditLogSPI.log("PURCHASE_PROCESSING")
   │  └─ AuditLogService (outbound-mongodb)
   │     └─ MongoDB
   ├─ InventoryRepositorySPI.deductAll(quantities, REJECT)  (one transaction, all-or-nothing)
   │  └─ InventoryService (outbound-postgres)
   │     └─ PostgreSQL (SELECT ... FOR UPDATE per product, sorted by name)
   └─ Completed: AuditLogSPI.log("INVENTORY_DEDUCTED") → 200 UiResponse(ShopPage), fresh page
      Rejected:  AuditLogSPI.log("PURCHASE_REJECTED") → 409 UiResponse(ShopPage) with shortage messages, nothing deducted
         └─ AuditLogService (outbound-mongodb)
            └─ MongoDB
```

### ProductApiReceiver (/api/products) - JSON API → REST/SOAP/Kafka → Kafka Delivery Topics

#### GET /api/products - Product List (JSON)
```
ProductApiReceiver.list()
└─ ProductsHandler.listAll()
   └─ InventoryRepositorySPI.findAll()
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
   ├─ InventoryRepositorySPI.deductAll(quantities, REJECT)  (one transaction, all-or-nothing)
   │  └─ InventoryService (outbound-postgres)
   │     └─ PostgreSQL (SELECT ... FOR UPDATE per product, sorted by name)
   └─ Completed: AuditLogSPI.log("INVENTORY_DEDUCTED") → 204
      Rejected:  AuditLogSPI.log("PURCHASE_REJECTED") → 409 with shortage messages, nothing deducted
         └─ AuditLogService (outbound-mongodb)
            └─ MongoDB
```

## Note: Kafka Delivery Receivers

The Kafka inbound delivery flows (FruitDeliveryReceiver, VegetablesDeliveryReceiver, etc.) are completions of the HTTP order cycles documented above. They are not independent flows—they are triggered by HTTP POST requests and form the response path through Kafka topics. 

See `architecture-flow-kafka-reference.md` for technical details on Kafka topic cycles, configuration references, and data persistence patterns.
