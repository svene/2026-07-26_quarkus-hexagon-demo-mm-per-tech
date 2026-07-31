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

## 4. UI modernization: Bulma CSS + htmx live updates (DONE)

Replaced the hand-rolled inline `<style>` blocks with Bulma, and made both pages update their
inventory/audit numbers live instead of relying on manual refresh.

- **Static assets**: Bulma (`css/bulma.min.css`) and htmx (`js/htmx.org/2.0.8/htmx.js`) are vendored
  locally (no CDN, no build step). They were originally placed under `resources/static`, which Quarkus
  does **not** auto-serve — moved to `inbound-rest/src/main/resources/META-INF/resources/` (the
  directory Quarkus does serve from the classpath root), confirmed by a new `StaticResourcesTest`.
- **Bulma for styling** — `AdminReceiver/admin.html` and `ShopReceiver/shop.html` now use Bulma
  classes (`table`, `box`, `field has-addons`, `columns`, etc.) instead of inline CSS.
- **Merged `/admin` + `/admin/audit`** into one page: inventory + ordering forms in a `column
  is-half` on the left, audit log in a `column is-half` on the right (widened from an initial
  two-thirds/one-third split, plus a fluid container and `overflow-x:auto` + `white-space:nowrap` on
  the audit table, so log lines don't wrap). `/admin/audit` as a standalone page is gone;
  `AdminReceiverTest`'s old audit-page tests were retargeted to `/admin` and the new fragment endpoints.
- **htmx polling for the admin inventory table & audit panel** — both have no user input to protect,
  so each is a self-polling element (`hx-trigger="every 3s" hx-swap="outerHTML"`) that fetches and
  replaces itself wholesale from `GET /admin/inventory-fragment` / `GET /admin/audit-fragment`.
- **htmx polling for shop inventory numbers** — `/shop`'s quantity `<input>`s must survive polling
  (a full swap would wipe what the customer is typing), so each "Available" cell has an id
  (`avail-{name}`) and a hidden poller (`hx-swap="none"`) fetches `GET /shop/inventory-fragment`,
  which returns only out-of-band `<td id="avail-...">` snippets — htmx merges them in by id without
  touching the inputs.
- **Real bug found during Playwright verification**: merging the audit log onto `/admin` means the
  audit "Details" column also contains product names, so `page.getByRole('row').filter({ hasText:
  productName })` in `admin.spec.ts` started matching audit rows too. Fixed by scoping all row/cell
  lookups to `page.locator('#inventory-body')`.
- Covered by: `AdminReceiverTest` (fragment endpoints), `ShopReceiverTest` (OOB fragment),
  `StaticResourcesTest` (asset serving), and the full Playwright suite (all 10 tests, run live against
  `mvn quarkus:dev` with real Postgres/MongoDB/Kafka).
- `architecture.puml` and `README.md` updated with the new routes and htmx/Bulma details.

## 5. Admin "Randomize (dev)" button for restocking (DONE)

Quick way to fill the 7 supplier order forms on `/admin` with plausible test data (product name +
quantity between 80 and 600) without typing values by hand — mirrors the `/shop` page's existing
randomize button.

- Considered a server-side approach first (a new `InventoryAPI.seedInitialInventoryIfEmpty()` +
  `@Observes StartupEvent` hook in `app-server`), but the user preferred a client-side-only button
  instead, matching `/shop`'s pattern more closely and requiring no backend changes at all.
- Each of the 7 order forms on `/admin/admin.html` now has `class="order-form"` and
  `data-default-name="..."` (Mango, Carrot, Milk, Cola, Chicken, Bread, Detergent — same example names
  already used as form placeholders). The "Randomize (dev)" button next to the "Restock Inventory"
  heading fills every form's `productName` with its default name and `quantity` with a random number
  in `[80, 600]`, pure client-side JavaScript.
- **Auto-submit via htmx (follow-up)**: originally the button only filled the fields and the user had
  to click each *Order* button by hand; changed so all 7 orders submit automatically. Each `.order-form`
  now also has `hx-post="<same as action>" hx-swap="none"` (no swap target needed — the inventory table
  and audit panel already self-refresh via the existing 3s htmx polling). After filling a form's
  fields, the JS calls `htmx.trigger(form, 'submit')` to fire the AJAX POST immediately.
- `AdminReceiver`'s 7 `order-*` endpoints now check the `HX-Request` header: `true` → `204 No Content`
  (htmx call, `hx-swap="none"` discards it anyway, so no need to fetch a full page); absent → the
  original `303` redirect to `/admin`, kept as a plain-form/no-JS fallback.
- Side effect: since the forms are `hx-post`-enabled generally, a manual click on an individual
  **Order** button also now submits via AJAX with no full-page reload — not just the Randomize path.
- Covered by an extended Playwright test in `admin.spec.ts`: fills and triggers all 7 forms, asserts
  no navigation occurred (`page.url()` stays `/admin`), and confirms two of the orders actually landed
  in the inventory table (proving the auto-submit worked, not just the fill). The 7 existing per-supplier
  manual-order tests still pass unchanged, since their completion check already polls via a manual
  `page.reload()`, independent of navigation.

## 6. Architecture diagram refactoring — technology-focused views (DONE)

Split the monolithic `architecture.puml` into 4 focused diagrams by technology:

- **`kafka-architecture.puml`** — all async messaging: Kafka topics, inbound receivers, core handlers, outbound emitters, external stubs
- **`rest-architecture.puml`** — HTTP request/response: Browser/REST clients, inbound adapters, core handlers, outbound HTTP services, external stubs
- **`soap-architecture.puml`** — SOAP supplier integration only: order endpoints, handlers, SOAP services, external SOAP stubs
- **`persistence-architecture.puml`** — data storage patterns: SPI interfaces, core handlers, outbound adapters, databases (Postgres transactional inventory, MongoDB append-only audit log)

Each diagram significantly reduces visual complexity compared to the original by focusing on one technology concern at a time. All diagrams keep core in the middle; left-to-right flow through core is not yet fully clean (left-to-right refactoring deferred to section 7).

## 7. Diagram left-to-right flow improvement (NOT STARTED)

Reorganize all architecture diagrams (main + 4 focused ones) to ensure **strict left-to-right dependency flow through core**:
- External sources / inbound → **Core** → outbound adapters → external systems
- No arrows crossing the core horizontally
- Visual clarity: where does data/requests come in, where do they go out

This is deferred because PlantUML's auto-layout makes it challenging to enforce; a manual coordinate-based approach or a different diagram tool might be needed for full control.

## 8. Clean separation: HTML interface vs JSON API (DONE)

Split the monolithic `inbound-rest` module into cleanly separated concerns:

- **Rename module**: `inbound-rest` → `inbound-http` (reflects that it handles HTTP, both HTML and JSON)
- **Create subpackage `inbound-http.jsonapi`**: `ProductApiReceiver` here; contains all JSON API endpoints (`/api/products/*`)
  - Request classes (`OrderRequest`, `PurchaseRequest`) organized under jsonapi subpackages (fruit, vegetable, dairy, beverage, meat, bakery, nonfood, cashpoint) since only consumed by ProductApiReceiver
- **Create subpackage `inbound-http.html`**: `AdminReceiver`, `ShopReceiver` here; contains all HTML UI endpoints (`/admin/*`, `/shop/*`)
- **Rationale**: the term "REST" conflates two different interaction styles — this makes it explicit: JSON API over HTTP is one thing, HTML interfaces over HTTP is another. The original REST meant request-response hypermedia; JSON over HTTP is just "HTTP JSON API"
- Updated module metadata (pom.xml), package structure, all imports in ProductApiReceiver
- Updated `architecture.puml` and focused diagrams (kafka, rest, soap, persistence) to show `inbound-http.html` and `inbound-http.jsonapi` subpackages
- All 30 tests pass (pure refactoring, no functional changes)

## Open questions

- Authentication/authorization is out of scope for this POC, but the separate routes (`/admin`, `/shop`) make it easy to add later.
- Section 7 (diagram refactoring) may need a different tool or manual layout if PlantUML cannot enforce the strict left-to-right constraint.
