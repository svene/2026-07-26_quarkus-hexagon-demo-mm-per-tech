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

#### GET /admin - Admin Dashboard
```
AdminReceiver.list()
└─ ProductsAPI.listAll()
   └─ ProductsHandler.listAll()
      └─ InventoryRepositorySPI.findAll()
         └─ InventoryService (outbound-postgres)
            └─ PostgreSQL (ProductEntity.listAll())
```

#### GET /admin/inventory-fragment - Inventory Update
```
AdminReceiver.inventoryFragment()
└─ ProductsAPI.listAll()
   └─ ProductsHandler.listAll()
      └─ InventoryRepositorySPI.findAll()
         └─ InventoryService (outbound-postgres)
            └─ PostgreSQL
```

#### GET /admin/audit-fragment - Audit Log Update
```
AdminReceiver.auditFragment()
└─ AuditLogAPI.recent(limit)
   └─ AuditLogHandler.recent()
      └─ AuditLogSPI.findRecent()
         └─ AuditLogService (outbound-mongodb)
            └─ MongoDB (AuditLogEntry)
```

#### POST /admin/order-fruits - HTML Form → REST Client → Kafka Delivery Topic
```
AdminReceiver.orderFruits()
└─ FruitsAPI.order(productName, quantity)
   └─ FruitsHandler.order()
      ├─ AuditLogSPI.log("FRUITS_ORDER_RECEIVED")
      │  └─ AuditLogService (outbound-mongodb)
      │     └─ MongoDB
      ├─ FruitSupplierSPI.placeOrder()
      │  └─ FruitSupplierService (outbound-httpclient)
      │     └─ FruitSupplierClient (REST)
      │        └─ FruitSupplierStub (external-outbound-rest, same Quarkus instance)
      │           └─ Emitter → fruit-deliveries-out channel
      │              └─ Kafka Topic: fruit-deliveries
      │                 └─ FruitDeliveryReceiver (@Incoming("fruit-deliveries"))
      │                    └─ InventoryAPI.updateFruitAmount()
      │                       └─ InventoryHandler.update()
      │                          ├─ AuditLogSPI.log("FRUIT_DELIVERY_RECEIVED")
      │                          ├─ InventoryRepositorySPI.addAmount()
      │                          │  └─ InventoryService (outbound-postgres)
      │                          │     └─ PostgreSQL
      │                          └─ AuditLogSPI.log("FRUIT_INVENTORY_UPDATED")
      └─ AuditLogSPI.log("FRUITS_ORDER_PLACED")
         └─ AuditLogService (outbound-mongodb)
            └─ MongoDB
```

#### POST /admin/order-vegetables - HTML Form → REST Client → Kafka Delivery Topic
```
AdminReceiver.orderVegetables()
└─ VegetablesAPI.order()
   └─ VegetablesHandler.order()
      ├─ AuditLogSPI.log("VEGETABLES_ORDER_RECEIVED")
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
└─ DairyAPI.order()
   └─ DairyHandler.order()
      ├─ AuditLogSPI.log("DAIRY_ORDER_RECEIVED")
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
└─ BeveragesAPI.order()
   └─ BeveragesHandler.order()
      ├─ AuditLogSPI.log("BEVERAGES_ORDER_RECEIVED")
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
└─ MeatAPI.order()
   └─ MeatHandler.order()
      ├─ AuditLogSPI.log("MEAT_ORDER_RECEIVED")
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
└─ BakeryAPI.order()
   └─ BakeryHandler.order()
      ├─ AuditLogSPI.log("BAKERY_ORDER_RECEIVED")
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
└─ NonFoodAPI.order()
   └─ NonFoodHandler.order()
      ├─ AuditLogSPI.log("NON_FOOD_ORDER_RECEIVED")
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
ShopReceiver.list()
└─ ProductsAPI.listAll()
   └─ ProductsHandler.listAll()
      └─ InventoryRepositorySPI.findAll()
         └─ InventoryService (outbound-postgres)
            └─ PostgreSQL
   └─ Filter in-stock products (availableAmount > 0)
```

#### GET /shop/inventory-fragment - Inventory Fragment
```
ShopReceiver.inventoryFragment()
└─ ProductsAPI.listAll()
   └─ ProductsHandler.listAll()
      └─ InventoryRepositorySPI.findAll()
         └─ InventoryService (outbound-postgres)
            └─ PostgreSQL
```

#### POST /shop/checkout - Customer Purchase
```
ShopReceiver.checkout(productNames[], quantities[])
└─ PurchaseAPI.purchase(items)
   └─ PurchaseHandler.purchase()
      ├─ AuditLogSPI.log("PURCHASE_RECEIVED")
      │  └─ AuditLogService (outbound-mongodb)
      │     └─ MongoDB
      ├─ For each item:
      │  └─ InventoryRepositorySPI.deductAmount()
      │     └─ InventoryService (outbound-postgres)
      │        └─ PostgreSQL (ProductEntity - deduct inventory)
      └─ AuditLogSPI.log("INVENTORY_DEDUCTED")
         └─ AuditLogService (outbound-mongodb)
            └─ MongoDB
```

### ProductApiReceiver (/api/products) - JSON API → REST/SOAP/Kafka → Kafka Delivery Topics

#### GET /api/products - Product List (JSON)
```
ProductApiReceiver.list()
└─ ProductsAPI.listAll()
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
└─ FruitsAPI.order()
   └─ FruitsHandler.order()
      ├─ AuditLogSPI.log("FRUITS_ORDER_RECEIVED")
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
└─ PurchaseAPI.purchase(items)
   └─ PurchaseHandler.purchase()
      ├─ AuditLogSPI.log("PURCHASE_RECEIVED")
      │  └─ AuditLogService (outbound-mongodb)
      │     └─ MongoDB
      ├─ For each item:
      │  └─ InventoryRepositorySPI.deductAmount()
      │     └─ InventoryService (outbound-postgres)
      │        └─ PostgreSQL
      └─ AuditLogSPI.log("INVENTORY_DEDUCTED")
         └─ AuditLogService (outbound-mongodb)
            └─ MongoDB
```

## Note: Kafka Delivery Receivers

The Kafka inbound delivery flows (FruitDeliveryReceiver, VegetablesDeliveryReceiver, etc.) are completions of the HTTP order cycles documented above. They are not independent flows—they are triggered by HTTP POST requests and form the response path through Kafka topics. 

See `architecture-flow-kafka-reference.md` for technical details on Kafka topic cycles, configuration references, and data persistence patterns.
