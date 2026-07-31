# Next steps

## 1. Customer UI — online shopping (DONE)

A dedicated HTML interface for customers to browse and purchase products, added alongside `/products` and `/admin`.

- `GET /shop` shows every in-stock product as a cart row (name, type, available amount, quantity input); one form submits the whole basket to `POST /shop/checkout`, which builds a `PurchaseItem` list and calls the existing `PurchaseAPI.purchase(...)`
- No core changes were needed — reuses `ProductsAPI.listAll()` and `PurchaseAPI.purchase(List<PurchaseItem>)` as-is
- **Randomize (dev) button**: client-side JavaScript only (no server round trip) that fills 2–4 random rows with a random quantity up to that row's available amount, so quantities don't have to be typed by hand during development
- Covered by `ShopReceiverTest` in `app-server`
- `architecture.puml` and `README.md` updated with `ShopReceiver`

## 2. Administrator UI — inventory management and ordering (DONE)

A dedicated HTML interface for supermarket staff, added alongside (not replacing) `/products`.

- **Inventory view**: table of all products with name, type, available amount — `GET /admin`
- **Ordering**: forms to reorder from each supplier, grouped by technology (REST/SOAP/Kafka) — `POST /admin/order-*`
- **Audit log view**: recent `AuditLogEntry` records from MongoDB (event + details + timestamp) — `GET /admin/audit`, backed by the new `AuditLogAPI` + `AuditLogHandler` + `AuditLogSPI.findRecent(limit)`
- `/products` was left untouched; `AdminReceiver` is a separate class with its own `/admin/order-*` endpoints so the two pages don't share redirect targets
- Covered by `AdminReceiverTest` in `app-server`
- `architecture.puml` updated with `AdminReceiver`, `AuditLogAPI`, `AuditLogHandler`, and the extended `AuditLogSPI`

## Open questions

- Authentication/authorization is out of scope for this POC, but the separate routes (`/products`, `/admin`, `/shop`) make it easy to add later.
- No further planned work at the moment — all items in this file are done.
