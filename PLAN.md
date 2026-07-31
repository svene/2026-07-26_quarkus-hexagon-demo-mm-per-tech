# Next steps

## 1. Customer UI — online shopping

A dedicated HTML interface for customers to browse and purchase products.

- New Qute template, separate from the admin page
- Show available products with current stock levels (read-only inventory view)
- Shopping cart: add/remove items, then submit the whole basket as one purchase
  - The backend `PurchaseAPI.purchase(List<PurchaseItem>)` already supports multi-item purchases
- The current 3-row purchase form in `/products` was a POC shortcut; this replaces it for the customer-facing path
- Route suggestion: `GET /shop` (HTML) + `POST /shop/checkout` (form submit)

## 2. Administrator UI — inventory management and ordering (DONE)

A dedicated HTML interface for supermarket staff, added alongside (not replacing) `/products`.

- **Inventory view**: table of all products with name, type, available amount — `GET /admin`
- **Ordering**: forms to reorder from each supplier, grouped by technology (REST/SOAP/Kafka) — `POST /admin/order-*`
- **Audit log view**: recent `AuditLogEntry` records from MongoDB (event + details + timestamp) — `GET /admin/audit`, backed by the new `AuditLogAPI` + `AuditLogHandler` + `AuditLogSPI.findRecent(limit)`
- `/products` was left untouched; `AdminReceiver` is a separate class with its own `/admin/order-*` endpoints so the two pages don't share redirect targets
- Covered by `AdminReceiverTest` in `app-server`
- `architecture.puml` updated with `AdminReceiver`, `AuditLogAPI`, `AuditLogHandler`, and the extended `AuditLogSPI`

## Open questions

- Authentication/authorization is out of scope for this POC, but the two separate routes make it easy to add later.
- Customer UI (`/shop`) is still open — see section 1 above.
