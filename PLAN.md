# Next steps

## 1. Customer UI — online shopping (DONE)

A dedicated HTML interface for customers to browse and purchase products.

- `GET /shop` shows every in-stock product as a cart row (name, type, available amount, quantity input); one form submits the whole basket to `POST /shop/checkout`, which builds a `PurchaseItem` list and calls the existing `PurchaseAPI.purchase(...)`
- No core changes were needed — reuses `ProductsAPI.listAll()` and `PurchaseAPI.purchase(List<PurchaseItem>)` as-is
- **Randomize (dev) button**: client-side JavaScript only (no server round trip) that fills 2–4 random rows with a random quantity up to 10 or that row's available amount, whichever is smaller, so quantities don't have to be typed by hand during development
- Covered by `ShopReceiverTest` in `app-server`
- `architecture.puml` and `README.md` updated with `ShopReceiver`

## 2. Administrator UI — inventory management and ordering (DONE)

A dedicated HTML interface for supermarket staff.

- **Inventory view**: table of all products with name, type, available amount — `GET /admin`
- **Ordering**: forms to reorder from each supplier, grouped by technology (REST/SOAP/Kafka) — `POST /admin/order-*`
- **Audit log view**: recent `AuditLogEntry` records from MongoDB (event + details + timestamp) — `GET /admin/audit`, backed by the new `AuditLogAPI` + `AuditLogHandler` + `AuditLogSPI.findRecent(limit)`
- Covered by `AdminReceiverTest` in `app-server`
- `architecture.puml` updated with `AdminReceiver`, `AuditLogAPI`, `AuditLogHandler`, and the extended `AuditLogSPI`

## 3. Removed the old `/products` HTML page (DONE)

Once `/admin` and `/shop` existed, `/products` (`ProductReceiver`) had nothing left that wasn't covered by one of the two new pages, so it was deleted.

- Deleted `ProductReceiver.java`, its `templates/ProductReceiver/` directory, and `ProductReceiverTest.java`
- The JSON API at `/api/products` (`ProductApiReceiver`) is untouched — flow tests and `CashpointStub` still use it exclusively
- Playwright suite `products.spec.ts` was split into `admin.spec.ts` (ordering tests, retargeted to `/admin`) and `shop.spec.ts` (purchase test, retargeted to `/shop`'s cart-row UI); `playwright.config.ts`'s `webServer.url` now points at `/admin`
- `architecture.puml`, `README.md`, `concepts.md` updated to remove `/products` references

## Open questions

- Authentication/authorization is out of scope for this POC, but the separate routes (`/admin`, `/shop`) make it easy to add later.
- No further planned work at the moment — all items in this file are done.
