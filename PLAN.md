# Next steps

## shop-ui: Customer UI — online shopping (DONE)

A dedicated HTML interface for customers to browse and purchase products.

- `GET /shop` shows every in-stock product as a cart row (name, type, available amount, quantity input); one form submits the whole basket to `POST /shop/checkout`, which builds a `PurchaseItem` list and calls the existing `PurchaseAPI.purchase(...)`
- No core changes were needed — reuses `ProductsAPI.listAll()` and `PurchaseAPI.purchase(List<PurchaseItem>)` as-is
- **Randomize (dev) button**: client-side JavaScript only (no server round trip) that fills 2–4 random rows with a random quantity up to 10 or that row's available amount, whichever is smaller, so quantities don't have to be typed by hand during development
- Covered by `ShopReceiverTest` in `app-server`
- `architecture.puml` and `README.md` updated with `ShopReceiver`

## admin-ui: Administrator UI — inventory management and ordering (DONE)

A dedicated HTML interface for supermarket staff.

- **Inventory view**: table of all products with name, type, available amount — `GET /admin`
- **Ordering**: forms to reorder from each supplier, grouped by technology (REST/SOAP/Kafka) — `POST /admin/order-*`
- **Audit log view**: recent `AuditLogEntry` records from MongoDB (event + details + timestamp) — `GET /admin/audit`, backed by the new `AuditLogAPI` + `AuditLogHandler` + `AuditLogSPI.findRecent(limit)`
- Covered by `AdminReceiverTest` in `app-server`
- `architecture.puml` updated with `AdminReceiver`, `AuditLogAPI`, `AuditLogHandler`, and the extended `AuditLogSPI`

## remove-products-page: Removed the old `/products` HTML page (DONE)

Once `/admin` and `/shop` existed, `/products` (`ProductReceiver`) had nothing left that wasn't covered by one of the two new pages, so it was deleted.

- Deleted `ProductReceiver.java`, its `templates/ProductReceiver/` directory, and `ProductReceiverTest.java`
- The JSON API at `/api/products` (`ProductApiReceiver`) is untouched — flow tests and `CashpointStub` still use it exclusively
- Playwright suite `products.spec.ts` was split into `admin.spec.ts` (ordering tests, retargeted to `/admin`) and `shop.spec.ts` (purchase test, retargeted to `/shop`'s cart-row UI); `playwright.config.ts`'s `webServer.url` now points at `/admin`
- `architecture.puml`, `README.md`, `concepts.md` updated to remove `/products` references

## bulma-htmx-ui: UI modernization: Bulma CSS + htmx live updates (DONE)

Replaced the hand-rolled inline `<style>` blocks with Bulma, and made both pages update their
inventory/audit numbers live instead of relying on manual refresh.

- **Static assets**: Bulma (`css/bulma.min.css`) and htmx (`js/htmx.org/4.0.0/htmx.js`) are vendored
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
  which returns only `<hx-partial id="avail-...">` snippets — htmx swaps each number into its cell by id without
  touching the inputs.
- **Real bug found during Playwright verification**: merging the audit log onto `/admin` means the
  audit "Details" column also contains product names, so `page.getByRole('row').filter({ hasText:
  productName })` in `admin.spec.ts` started matching audit rows too. Fixed by scoping all row/cell
  lookups to `page.locator('#inventory-body')`.
- Covered by: `AdminReceiverTest` (fragment endpoints), `ShopReceiverTest` (partials fragment),
  `StaticResourcesTest` (asset serving), and the full Playwright suite (all 10 tests, run live against
  `mvn quarkus:dev` with real Postgres/MongoDB/Kafka).
- `architecture.puml` and `README.md` updated with the new routes and htmx/Bulma details.

## admin-randomize: Admin "Randomize (dev)" button for restocking (DONE)

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
- **Auto-submit via htmx (follow-up)** — *reverted by `randomize-fill-only`: Randomize only fills again, a separate
  Submit all button submits*: originally the button only filled the fields and the user had
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

## tech-diagrams: Architecture diagram refactoring — technology-focused views (DONE)

Split the monolithic `architecture.puml` into 4 focused diagrams by technology:

- **`kafka-architecture.puml`** — all async messaging: Kafka topics, inbound receivers, core handlers, outbound emitters, external stubs
- **`rest-architecture.puml`** — HTTP request/response: Browser/REST clients, inbound adapters, core handlers, outbound HTTP services, external stubs
- **`soap-architecture.puml`** — SOAP supplier integration only: order endpoints, handlers, SOAP services, external SOAP stubs
- **`persistence-architecture.puml`** — data storage patterns: SPI interfaces, core handlers, outbound adapters, databases (Postgres transactional inventory, MongoDB append-only audit log)

Each diagram significantly reduces visual complexity compared to the original by focusing on one technology concern at a time. All diagrams keep core in the middle; left-to-right flow through core is not yet fully clean (left-to-right refactoring deferred to `diagram-left-to-right`).

## diagram-left-to-right: Diagram left-to-right flow improvement (DISCARDED)

Reorganize all architecture diagrams (main + 4 focused ones) to ensure **strict left-to-right dependency flow through core**:
- External sources / inbound → **Core** → outbound adapters → external systems
- No arrows crossing the core horizontally
- Visual clarity: where does data/requests come in, where do they go out

This is deferred because PlantUML's auto-layout makes it challenging to enforce; a manual coordinate-based approach or a different diagram tool might be needed for full control.

Discarded 2026-10-05: not worth the effort for the demo.

## html-json-separation: Clean separation: HTML interface vs JSON API (DONE)

Split the monolithic `inbound-rest` module into cleanly separated concerns:

- **Rename module**: `inbound-rest` → `inbound-http` (reflects that it handles HTTP, both HTML and JSON)
- **Create subpackage `inbound-http.jsonapi`**: `ProductApiReceiver` here; contains all JSON API endpoints (`/api/products/*`)
  - Request classes (`OrderRequest`, `PurchaseRequest`) organized under jsonapi subpackages (fruit, vegetable, dairy, beverage, meat, bakery, nonfood, cashpoint) since only consumed by ProductApiReceiver
- **Create subpackage `inbound-http.html`**: `AdminReceiver`, `ShopReceiver` here; contains all HTML UI endpoints (`/admin/*`, `/shop/*`)
- **Rationale**: the term "REST" conflates two different interaction styles — this makes it explicit: JSON API over HTTP is one thing, HTML interfaces over HTTP is another. The original REST meant request-response hypermedia; JSON over HTTP is just "HTTP JSON API"
- Updated module metadata (pom.xml), package structure, all imports in ProductApiReceiver
- Updated `architecture.puml` and focused diagrams (kafka, rest, soap, persistence) to show `inbound-http.html` and `inbound-http.jsonapi` subpackages
- All 30 tests pass (pure refactoring, no functional changes)

## browser-templating: Replace Qute with browser-side hono/html templating (DONE)

Replace the Qute templates in `inbound-http-html` with the approach from
[svene/2026-09-03_hypermedia-quarkus-browser-hono](https://github.com/svene/2026-09-03_hypermedia-quarkus-browser-hono):
receivers return a JSON `{ route, vm }` envelope, and an htmx 4 extension renders the matching
hono/html template in the browser. Large change — plan, decisions and progress are tracked in
[`docs/architecture/browser-templating_wip.md`](docs/architecture/browser-templating_wip.md).

## live-updates: `/shop` updates as soon as the inventory changes, via an SSE event (DONE)

Replaced the 3 s polling on `/shop` with a server push. Design and the decisions that led to it
(2026-10-01) are in [`docs/architecture/live-updates_wip.md`](docs/architecture/live-updates_wip.md).
- **Scope `/shop` only.** `/admin` keeps polling: its inventory table has no input to protect, and
  the audit panel is polled anyway, so a stream would add a second mechanism for little gain.
- **An event, not data.** `GET /shop/events` (SSE; moved to `GET /inventory/events` by `admin-live-inventory`) sends `inventoryChanged`. The page then
  re-fetches `GET /shop/inventory-fragment` and morphs it in (from `shop-product-set-refresh`).
  The original idea (multipart stream into a hidden data island + `hx-live` bindings) was dropped
  as far more machinery for the same result. The client-side `hx-live` part moved to `hx-live-ui`.
- **Core:** `InventoryChangesHandler` (JDK `Flow`/`SubmissionPublisher`, no reactive library in
  core), published to by `InventoryHandler` and `PurchaseHandler` after a committed change. Not an
  SPI: the `inbound_adapters_do_not_use_spis` ArchUnit rule forbids that, and notifications flow
  core → inbound adapter anyway. In-process only (single instance).

## split-inventory: Locations: central DC, 3 stores, online dark store (DONE)

Replace the single inventory with one stock per location: a central DC that receives all supplier
deliveries, 3 physical stores (deducted by cashpoint sales) and an online fulfilment centre (deducted
by `/shop` and JSON API checkouts). Locations pull replenishment from the DC; reorder levels are
learned from sales (no hand-maintained min/max); falling below them fires a CDI `StockBelowMinimum`
event handled by a new `inbound-event` module, which requests from the DC or orders from the
supplier. Three phases plus phase 4 items (supplier lead time done; direct store delivery dropped as not
needed for the demo; in-transit transfers done). Plan, decisions and progress are tracked in
[`docs/architecture/split-inventory_wip.md`](docs/architecture/split-inventory_wip.md).

## randomize-fill-only: Randomize (dev) buttons only fill the inputs, the user submits (DONE)

Change the *Randomize (dev)* buttons on `/admin` and `/shop` so that they **only fill the input
fields** with random values and never submit anything themselves. Submitting stays a deliberate
user action (the existing per-form **Order** buttons on `/admin`, **Checkout** on `/shop`).

Current state:
- `/admin` (`shells/admin.html`): fills every order form (default product name + quantity 80–600)
  and then calls `htmx.trigger(form, 'submit')` on all 7 forms, i.e. it orders immediately.
  This is the part that has to change.
- `/shop` (`shells/shop.html`): already only fills 2–4 random `.qty-input`s (≤ 10 and ≤ the row's
  available amount); it doesn't submit. Keep that behavior.
- Both scripts are inline `<script>` blocks in the shell HTML files, written as delegated click
  handlers because the button is rendered into `#app` later by the hono templates.

Option to slim down the shells: let the server generate the random values instead of inline
JavaScript, e.g. the button does `hx-get="/admin/randomize"` / `hx-get="/shop/randomize"` and gets
back the usual `{ route, vm }` envelope with the VM's input values pre-filled, rendered by the same
hono template. That removes both inline scripts from the shells, and the server-side shop variant
can take the current stock into account directly. Only worth it if it really is simpler than moving
the JS; decide while planning.

Decided: `/admin` gets an additional **Submit all** button next to *Randomize (dev)* that submits
all 7 order forms (what Randomize used to do in one click); the per-form **Order** buttons stay.
So restocking everything is now Randomize → (optionally edit) → Submit all.

Playwright: `admin.spec.ts` `randomize button fills every order form and submits all 7 orders…`
must be split: Randomize fills the fields and submits nothing; Submit all then sends all 7 orders
without a page reload.

Done (client-side variant, server-generated values rejected as not worth it):
- The inline `<script>` blocks are gone from `shells/admin.html` and `shells/shop.html`; the
  delegated click handlers now live next to the templates in `admin.ts` / `shop.ts`, bundled into
  `hx-hono.js` by esbuild. Since both pages load that one bundle, the button IDs are page-specific
  (`admin-randomize-btn`, `admin-submit-all-btn`, `shop-randomize-btn`).
- Submit all calls `form.requestSubmit()` per order form, i.e. exactly what clicking its **Order**
  button does, including the browser's `required` check (an empty form isn't sent).
- `admin.spec.ts`: "randomize button only fills every order form, it submits nothing" (no POST to
  `/admin/order-*`) and "submit all button sends all 7 filled order forms via htmx without a reload".

## shop-product-set-refresh: `/shop` picks up newly stocked products (DONE)

Problem: `/shop` didn't show a product that came into stock while the page was open (only after a
manual reload). The 3 s poll answered with one `<hx-partial id="avail-<name>">` per product, which
only updated the "Available" cell of rows that already existed. An empty shop didn't poll at all,
and a sold-out product kept its row (showing 0).

Done with htmx 4's built-in morph swap, simpler than the originally planned set comparison:
- `ShopPage` always renders `<div id="shop-products" hx-get="/shop/inventory-fragment"
  hx-trigger="every 3s" hx-swap="innerMorph">`, also in the empty state.
- `GET /shop/inventory-fragment` returns route `ShopProducts` (was `ShopAvailability`;
  `ShopProductsVM`, was `ShopAvailabilityVM`) with the in-stock products only. The browser renders
  the whole products section (empty-state message or cart form) and morphs it in. No set comparison
  on the server, no `hx-include`.
- Rows are keyed `id="row-<name>-<type>"` (the table is unique on name+type; this also fixes the old
  `avail-<name>` collision). New rows get inserted, sold-out rows get removed, and rows that stay are
  patched in place.
- The quantity `<input>` has **no `value` attribute**. htmx's morph (`htmx.js` `#copyAttributes`)
  sets `input.value` only when the new markup's `value` attribute differs, so the old `value=""` would
  have reset every unfocused typed quantity. Without it, typed and randomized values survive. A
  focused input is never overwritten or moved.
- Covered by: `ShopReceiverTest.inventory_fragment_returns_only_products_in_stock`, and
  `shop.spec.ts` "an open shop page picks up a newly stocked product without a reload and keeps
  typed quantities" (types a quantity, stocks a new product through `/admin` in a second tab, expects
  the new row without a reload while the typed value stays).

## hx-live-ui: client-side reactive UI with htmx 4's `hx-live` (DONE)

**First task (DONE 2026-10-02): replace the unused Alpine.js dark/light theme.** Alpine and
`js/main.js` were vendored but loaded by neither shell, so dark mode was dead code.

Decided: **CSS + toggle, no `hx-live`.** Without a saved choice Bulma 1.x follows the OS
(`prefers-color-scheme`). A toggle button in both shells (outside `#app`, so swaps never replace
it) flips `<html data-theme="dark|light">`, which Bulma reads, and stores the choice in
`localStorage`. `js/theme.js` is loaded synchronously in `<head>`, so a saved choice is applied
before the first paint; the button's ☾/☀ icon is pure CSS (`css/theme.css`). `hx-live` didn't fit:
it runs after load (flash) and the state lives outside the DOM. `main.js` and `js/alpinejs/` are
deleted, which also frees `hx-live`'s short `:<target>` syntax. Playwright: `shop.spec.ts`
`theme toggle switches dark/light and remembers the choice across reloads`.

Split off from `live-updates` (2026-10-01): live updates come from an SSE event that makes the page
re-fetch the products fragment (morph swap), so `hx-live` is **not** a transport or data store.
It is used for UI state derived **in the browser** from what's on the page, written as inline
expressions next to the elements they affect.

How `hx-live` works (4.0.0, vendored as `js/htmx.org/4.0.0/ext/hx-live.js`, loaded by both shells):
- Bindings are attributes `hx-live:<target>="<expr>"` or the short form `:<target>="<expr>"`
  (available because Alpine.js is gone). Targets: `text`, attributes like `disabled`, classes like
  `.is-danger`. Inside an expression `this` is the element; `q('.sel')` queries the page and
  supports array methods (`some`, `every`, `filter`, `reduce`, …).
- Every expression re-runs after DOM mutations (also after a morph swap of `#shop-products`) and
  after `input`/`change` events, batched into one microtask, deferred during htmx swaps.
- Setting `input.value` from code fires no event: the Randomize handlers therefore dispatch an
  `input` event on `document` so the bindings recompute.

**Second task (DONE 2026-10-02): derived UI.**
- `/shop` (`shop.ts`):
  - Quantity input `:.is-danger="this.valueAsNumber > +this.max"`. `max` is the server-rendered
    stock; when stock drops while the customer is typing, the SSE-triggered morph lowers `max` and the
    flag reacts without a reload. No extra ids needed (the earlier `#qty-…`/`#avail-…` idea).
  - *Purchase* `:disabled` while no quantity > 0 is entered or any input is over its `max`.
  - Cart summary `#shop-cart-summary` (`:text`, "N products, M items") next to the buttons.
- `/admin` (`admin.ts`):
  - Quantity inputs get `max="2000"` (mirrors `@Min(1) @Max(2000)` on the `*Order` records).
  - Each *Order* button `:disabled="!this.form.checkValidity()"`; *Submit all* is disabled until
    every order form is valid.
- Playwright: `shop.spec.ts` "the cart flags quantities above stock, disables Purchase, and reacts
  when stock drops under the customer" (a second tab buys stock away, no reload);
  `admin.spec.ts` "order buttons stay disabled until their form is valid; submit all until every
  form is".

Decided against:
- `/admin` inventory low-stock highlight or total: the server knows the numbers; if wanted, render
  it there.
- Randomize / *Submit all* as `hx-live` effects: they are one-shot actions, not derived state, so
  they stay plain event listeners in `admin.ts`/`shop.ts`.
- `/admin` audit panel client-side filter (2026-10-02): skipped, the panel stays as it is. It would
  only have searched the 100 entries shown, and needed the input outside the 3 s `outerHTML` poll.

Ground rules:
- Client-side checks are UX only; server validation stays authoritative
  (`docs/architecture/validation.md`). The race between "button enabled" and "checkout committed"
  is still decided by the server (409 on insufficient stock).
- Prefer native HTML constraints (`required`, `min`, `max`; htmx calls `reportValidity()` before
  sending), then `hx-live` bindings for what HTML can't express, then hand-written JS.
- Keep expressions short (hx-live warns above 16 ms per recompute) and let them read values the
  server already renders (e.g. `max`) instead of duplicating data.

## admin-live-inventory: Reactive inventory panel with per-row restock on `/admin` (DONE)

Give `/admin` a current-inventory panel like the one on `/shop`, placed above the existing
*Restock Inventory* panel. It is reactive, and every row has its own restock action.

Current state: `/admin` already shows a *Current Inventory* table above *Restock Inventory*
(`AdminInventory` in `admin.ts`). It is a `<tbody id="inventory-body">` that polls
`/admin/inventory-fragment` every 3 s and is replaced with `outerHTML`. It has no inputs, which was
why `live-updates` left `/admin` on polling. Per-row inputs change that: a 3 s `outerHTML`
replacement would wipe typed quantities.

Observed (2026-10-02): a newly ordered product shows up in that table only after a manual reload, so
it isn't reactive today, despite the poll. Suspected causes, check while implementing: the empty
state (`No products in inventory yet.`) renders no polling element at all (the same bug `/shop` had
before `shop-product-set-refresh`); and/or the `<tbody>` fragment doesn't survive the `outerHTML`
swap. Either way the SSE + morph approach replaces the poll.

Intended shape (to be confirmed while planning):
- The new panel replaces the polled table rather than sitting next to it, so `/admin` keeps a
  single inventory view.
- Reactive like `/shop`: listen to the existing `inventoryChanged` SSE event (an SSE connect element
  in `shells/admin.html`, outside `#app`) and re-fetch the fragment with `innerMorph`. Rows are keyed
  `row-<name>-<type>` (as in `shop-product-set-refresh`), and the quantity input has no `value`
  attribute, so typed quantities and focus survive a refresh.
- Per row: a quantity input and a *Restock* button that orders more of that product (same name)
  through the supplier for its `ProductType`: FRUIT/VEGETABLE/DAIRY → REST, BEVERAGE/MEAT/BAKERY →
  SOAP, NON_FOOD → Kafka. That reuses the existing `/admin/order-*` endpoints and Handlers, so no
  new core code. Client-side validation as in `hx-live-ui`: `min="1" max="2000"` and the button
  disabled via `hx-live` while the row's form is invalid.

Decided:
- Per-row errors (e.g. a 400 from the server) are shown in an extra column at the very right of the
  table.
- *Restock Inventory* (the 7 order forms, Randomize, Submit all) stays: it is needed for products
  that aren't in stock yet.

Postponed until after `split-inventory`: whether every stock change (also cashpoint sales) reaches
the panel as an SSE event. Orders reach the inventory asynchronously (supplier → Kafka delivery →
`InventoryHandler`), which already publishes to `InventoryChangesHandler`.

Done (2026-10-02):
- One SSE endpoint for both pages: `GET /inventory/events` in the new `InventoryEventsReceiver`
  (moved out of `ShopReceiver`, was `GET /shop/events`). Both shells connect to it outside `#app`;
  `shells/admin.html` now loads `hx-sse.js` (after `hx-hono.js`).
- `admin.ts`: `<div id="admin-inventory" hx-get="/admin/inventory-fragment" hx-trigger="inventoryChanged
  from:body" hx-sync="this:replace" hx-swap="innerMorph">`; the `AdminInventory` route renders the whole
  table (or the empty-state text), so the 3 s `<tbody>` poll is gone, and with it the "only after a
  reload" bug. Columns: Name | Type | Available | Restock | (error). Rows sorted by name
  (case-insensitive, then type) like `/shop`, in `AdminReceiver.products()`.
- Restock form inside the cell: hidden `productName`, quantity `min="1" max="2000" required` without a
  `value` attribute (kept after a restock), button `:disabled="!this.form.checkValidity()"`; posts to
  the type's `/admin/order-*` endpoint (`RESTOCK_ACTIONS` map in `admin.ts`), `hx-target="next
  .restock-error"`. Known trade-off: the next inventory refresh clears a row's error message.
- Tests: `InventoryEventsReceiverTest` (the SSE test moved from `ShopReceiverTest`); `admin.spec.ts`
  "restocking from an inventory row raises its amount live and keeps quantities typed into other rows",
  "the restock button of a row is disabled while its quantity is outside 1-2000"; the existing
  "ordering … adds it to the inventory table" tests now wait for the row without reloading.

## two-pods: Run in two pods without downtime (ANALYSIS ONLY, no code)

The demo is developed as if it ran in Kubernetes with two pods. Started as the question whether the CDI `@ObservesAsync`
events should go through a Kafka topic the app sends to itself (former item `kafka-internal-events`). The answer: Kafka
for the cross-pod SSE fan-out, but not for the work-triggering events (that needs a transactional outbox, so catch up at
the period close instead). Two pods also need a single-instance period close, idempotent Kafka consumers, schema
migrations instead of `drop-and-create`, health probes and the stubs as separate deployments. The user decided not to
implement it for now (2026-10-04; confirmed 2026-10-05 - keep the analysis up to date as features are added). Analysis and suggested order:
[`docs/architecture/two-pods_wip.md`](docs/architecture/two-pods_wip.md).

## audit-log-page: The audit log on a page of its own (DONE)

Done 2026-10-05 at the user's request. The audit panel left `/admin` (it polled `GET /admin/audit-fragment` every
3 s, 100 entries). New `AuditLogReceiver`: `GET /audit-log` (static shell `shells/audit-log.html` without hx-sse,
with a *Refresh* button that reloads `#app`) and `GET /audit-log/page` → `UiRoute.AuditLogPage`,
`AuditLogPageVM(auditEntries, limit)` with the latest 300 entries, newest first (`audit-log.ts`). Removed:
`GET /admin/audit-fragment`, `AuditPanelVM`, `UiRoute.AuditPanel`, `auditEntries` in `AdminPageVM`. The landing page
links `/audit-log` as a fourth entry point. Tests: `AuditLogReceiverTest` (incl. the 300 cap), `audit-log.spec.ts`.

## store-capacity: Store sales driven by customers in the store, not a fixed timer (DONE)

Added 2026-10-04 at the user's request. Today the cashpoint traffic of the physical stores comes from one timer:
`CashpointStub` (`external-inbound-kafka`, `@Scheduled(every = "10s")`) picks a random store each tick and sells from its
stock, so every store sells at the same average rate, whatever its size.

### Decisions (elaborated 2026-10-05)

- **The tills are the bottleneck.** Each store has a capacity (customers inside at the same time) and a number of
  tills. Only so many customers per time can pay; while they queue at the tills the store stays full, and a customer
  who arrives at a full store is turned away (a lost customer, who buys nothing). **One checkout per customer who
  pays** - no thinning out, no aggregation.
- **Why not realistic numbers everywhere:** at 1 day = 1 min (`inventory.demand-period`) a store with hundreds of
  customers and a realistic stay produces thousands of checkouts per demo minute (≈ 32 per customer inside), far
  beyond what the replenishment (order caps of 2000, lead/transit time) and the live UIs handle. Rejected for that:
  smaller capacities alone, 1 day = 10 min, sending only every Nth checkout, aggregated checkouts with an initial fill
  and higher caps. The **till time is the one deliberate exception** to the time scale: it is set in demo time (15 s),
  which caps the purchase rate. Everything else follows 1 day = 1 min.
- **Stores** (the capacity column was dropped after the dev run, see Result):

  | Store | Capacity | Tills | Max checkouts per demo minute (15 s per customer) |
  |---|---|---|---|
  | Zurich | ~~60~~ | 4 | 16 |
  | Basel | ~~40~~ | 2 | 8 |
  | Bern | ~~20~~ | 1 | 4 |

  ≈ 28 checkouts/min for the chain (today ≈ 6). Numbers get tuned in the dev app.
- **Time in the store:** 30-60 real minutes → 1.25-2.5 s at 1 day = 1 min (uniform, i.e. jittered).
- **Arrivals** follow a rush-hour curve over the demo day, relative to the store's till throughput (from ≈ 0.3× to
  ≈ 1.5×), so the store fills up and turns customers away at the peak and empties again afterwards.
- **Only stub configuration.** Capacity and tills are properties of the external checkout systems; core's `Store` and
  the app don't change (same `cashpoint-purchases` topic, same `CashpointReceiver`). The learned reorder levels differ
  per store by themselves, since demand differs.
- **No occupancy in the UI** in this item: the app would need a new inbound (door counters). The stub logs occupancy,
  till queue, served and lost customers. Follow-up item `store-occupancy` (TO ELABORATE).
- **Two pods:** the simulation state is the external system's own state (in the stub, in memory) - fine. That the stub
  runs in every pod is the existing open point "stubs as separate deployments" in
  [`two-pods_wip.md`](docs/architecture/two-pods_wip.md); this item doesn't make it worse.

### Plan (APPROVED 2026-10-05)

1. **Simulation model** (`external-inbound-kafka`, package `cashpoint`, plain Java, no Quarkus):
   - `StoreConfig(String storeId, int capacity, int tills)`.
   - `StoreSimulation` - the state of one store: shoppers (each with the time it finishes shopping), the till queue
     (FIFO), each till's busy-until time, counters for served and lost customers. One method
     `int tick(Instant now, Duration dt)` advances the store and returns the number of customers who paid in this
     tick (= checkouts to send):
     1. arrivals: Poisson-distributed count with mean `arrivalRate(now) × dt`; each enters if occupancy < capacity,
        else is counted as lost;
     2. shoppers whose shopping time is over join the till queue;
     3. every free till takes the next customer from the queue for one till time; a customer whose till time is over
        has paid and leaves (frees a place).
     Occupancy = shoppers + till queue + customers at a till.
   - `arrivalRate(now)` = till throughput (`tills / till time`) × rush factor; the rush factor is a sine over the
     demo day between 0.3 and 1.5 (constants in the class).
   - Randomness through an injected `RandomGenerator`, durations through a `SimulationTiming` record (`day`,
     `tillTime`); stay = `day × uniform(30, 60) / 1440`, till time = `tillTime ± 20 %` (like the supplier lead time
     and the carrier transit time).
2. **`CashpointStub`** keeps its job (read stock via `ProductsApiClient`, build a basket, send a `PurchaseRequest`),
   but is driven by the simulations:
   - `@Scheduled(every = "${cashpoint-stub.tick}", delayed = "30s", concurrentExecution = SKIP)`; each tick calls
     `tick()` on the three `StoreSimulation`s and sends one checkout per paying customer (today's basket logic:
     2-4 in-stock products, 1-3 units each; nothing in stock → the customer leaves without a purchase, as today).
   - Every 10 s it logs per store: occupancy / capacity, till queue, served and lost since the last log line.
   - Store configs come from the config below (`@ConfigMapping(prefix = "cashpoint-stub")`); `STORE_IDS` goes.
3. **Config** (`app-server/application.properties`, new section "Cashpoint stub", like the other stubs):
   ```
   cashpoint-stub.tick=100ms
   cashpoint-stub.day=1m
   cashpoint-stub.till-time=15s
   cashpoint-stub.stores.zurich.capacity=60
   cashpoint-stub.stores.zurich.tills=4
   cashpoint-stub.stores.basel.capacity=40
   cashpoint-stub.stores.basel.tills=2
   cashpoint-stub.stores.bern.capacity=20
   cashpoint-stub.stores.bern.tills=1
   %test.cashpoint-stub.tick=off
   ```
   plus `-Dcashpoint-stub.tick=off` in `e2e-playwright/playwright.config.ts`. Today the stub starts selling after
   30 s in tests and e2e, too, where only the tests should move stock. (`tick=off` like `inventory.demand-period=off`,
   instead of the separate `enabled` switch mentioned during elaboration - one property fewer.) `cashpoint-stub.day`
   is its own property, since `inventory.demand-period` is `off` in tests.
4. **Tests** (new `external-inbound-kafka/src/test`, JUnit 5 + AssertJ from the parent POM): `StoreSimulationTest`
   with a seeded `RandomGenerator` and a hand-moved clock:
   - a customer who entered pays exactly once, after shopping time + till time;
   - occupancy never exceeds capacity; arrivals at a full store count as lost;
   - with 1 till, paying customers per minute ≤ 60 s / till time (with jitter: a tolerance);
   - more tills → more paying customers under the same arrivals.
   The existing `CashpointViaKafkaFlowTest` stays as it is (it publishes its own purchases).
5. **Docs** (update-architecture-docs skill): `concepts.md` (cashpoint stub section), `README.md` (simulated
   checkouts), `architecture-module-participants.md` (`CashpointStub`, `StoreSimulation`),
   `architecture-flow-kafka-reference.md` (producer line), `two-pods_wip.md` (`@Scheduled` every 10 s → tick);
   `docs/ai/session-notes.md` baseline. New PLAN item `store-occupancy` (TO ELABORATE).
6. **Verification**: `external-inbound-kafka` unit tests, full app-server suite, e2e (`--retries=0`); then the dev
   app: watch the stub's log lines over a few demo days (full stores at the peak, lost customers) and check that the
   learned reorder levels differ per store; tune capacities/tills/till time if the volume is off.

Result 2026-10-05: implemented as planned (staged). `StoreSimulationTest` 6/6, app-server 151, e2e 26/26 (first run).
Deviations: the snapshot also counts *entered* customers (the test checks entered = paid + still inside); while at it,
`order_fruits_returns_200_with_empty_body` and the new audit-log order test wait for the Banana delivery at the DC -
it could land in the next test's freshly reset inventory (seen once).

Dev run (≈ 2 min): the purchase rate is as planned (Zurich ≈ 16/min at its 4-till cap, Basel ≈ 6, Bern ≈ 4; ≈ 26
for the chain), but **the capacity never binds**: 5-11 customers in Zurich, none turned away. With a stay of only
1.25-2.5 s almost everyone inside is at the tills, and the queue grows only while arrivals exceed the till rate - for
≈ 20 s of the 1-min day at 1.5×, i.e. ≈ 3 extra customers in Zurich. Decided (user): **drop the capacity** - only the
tills drive the purchases; nobody is turned away. Removed from `StoreSimulation`, `CashpointStubConfig`, the config
and the docs; the "never more than its capacity" test went. `StoreSimulationTest` 5, app-server 151; dev run as before
(Zurich 10-11 inside, 6 at the tills). Side note: at 15 s per customer one till serves only 4 customers per demo day,
so the rush-hour curve is hardly visible against the random arrivals. The per-store reorder levels were not checked
yet (needs a longer dev run).

**Capacity reinstated** (2026-10-05, with `store-occupancy`): without it nobody is turned away, so opening a till
could not raise sales. Now with a rush peak of 2.5× (was 1.5×) and capacities that fit the compressed time scale
(Zurich 16, Basel 10, Bern 6) - see `store-occupancy`.

## dc-seed: The DC starts with stock instead of empty (DONE)

Added 2026-10-05 at the user's request. Today the DC is empty at startup and after the admin reset: the Flyway
migrations only create the schema, nothing inserts stock, and a product's stock row only appears with its first
supplier delivery. The automatic stages build on existing rows only (`AutoPurchasingReceiver` re-checks the products
the DC already carries; the stores request what the DC carries), so after a fresh start or a reset nothing happens
until someone orders each product by hand - and the store simulation's customers find nothing to buy.

Proposal: seed the DC with every product of the catalog at its cold-start level, so the whole chain runs on its own.

Decided 2026-10-05:
- **How it enters: as supplier orders** - the seed places one order per product through the commodity Handlers, like
  the admin order forms; the stock arrives as ordinary supplier deliveries (lead time, audit trail, open supplier
  orders on `/admin`).
- **Amount: one fixed number for every product**: 500. When it arrives, the stores and the online FC pull their
  cold-start max at once (3 × 47 + 68 ≈ 210 per product), which leaves ≈ 290 at the DC - above its cold-start reorder
  point of 136, so it doesn't reorder right away; and well below the 2000 cap per supplier order.
- **Catalog in core** (`cross.products.Catalog`); the admin order forms are rendered from it (one list, not two).
- **When:** after startup and after the admin reset, through one core Handler.
- **Two pods:** "DC has no stock rows and no open supplier orders", checked and recorded in one transaction under a
  Postgres advisory lock.
- **Switch** `inventory.dc-seed.enabled`, off in `%test` and e2e.
- **Origin `SEED`** next to `MANUAL` and `AUTOMATIC`, visible on `/admin` and in the audit log.

Consequences of going through the suppliers:
- **Startup timing.** The REST/SOAP supplier stubs run in the same app; placing orders in a `StartupEvent` observer
  may call them before the HTTP server listens. The startup seed has to run a little later (e.g. a one-off
  `@Scheduled(delayed = ...)`, like the cashpoint stub's 30 s).
- **Idempotency gets harder** (two pods, or a restart while seed orders are still open): the check becomes "the DC
  has no stock rows *and* no open supplier orders", taken under a lock so that two pods can't both pass it.

### Plan (APPROVED 2026-10-05)

1. **Catalog** (core, `cross.products`): `record CatalogProduct(String name, ProductType type)` and
   `Catalog.PRODUCTS` - the 28 products of today's order forms, in their order (4 per type, 7 types). Only a list:
   orders of other names stay allowed (the e2e tests order unique product names through the JSON API).
2. **Origin `SEED`** (`SupplierOrderOrigin`, javadoc "the DC was seeded: it was empty after a start or a reset").
   Migration **`V3__seed_origin.sql`**: the `supplier_order.origin` check constraint gets `'SEED'` (drop and re-add;
   V1/V2 stay untouched). `replenishment_request.origin` is not affected.
3. **SPI** `SupplierOrderRepositorySPI.openSeed(List<CatalogProduct> products, int quantity)` → `List<SupplierOrder>`:
   in one transaction, `pg_advisory_xact_lock(<constant>)` (there is no row to lock yet), then - only if the DC has
   no stock row and there is no open supplier order - one `SEED` order per product; else an empty list. Implemented
   in outbound-postgres (`SupplierOrderService` + a `StockTable`/`SupplierOrderTable` count query each).
4. **Core**: `PurchasingHandler.seedDc(int quantity)` - `openSeed(Catalog.PRODUCTS, quantity)`, then audit
   `DC_SEEDED` ("28 products × 500") and `place(order)` for each, the same switch over `ProductType` as the automatic
   orders. A supplier that is down cancels its own orders (as today); the seed doesn't retry them - the next check
   finds open orders of the others and does nothing, and those products then come in only by hand. (Acceptable for a
   demo; noted in the javadoc.)
5. **Trigger** (inbound-event, new `DcSeedReceiver`, switch `inventory.dc-seed.enabled` + quantity
   `inventory.dc-seed.quantity=500`), following the user's preference for events over Handler → Handler calls:
   - `@ObservesAsync InventoryReset` → `seedDc` - right after the admin reset;
   - `@ObservesAsync LevelsRecalculated` → `seedDc` - covers the startup without a timer of its own: the first period
     close comes 1 min after the start, when the supplier stubs in the same app are reachable. The check is cheap and
     idempotent, so running it at every period close is harmless (it seeds only an empty DC).
   - Failures are audit-logged (`DC_SEED_FAILED`), like the other receivers.
   Result in dev: seed orders ≈ 1 min after start (right away after a reset), DC stocked ≈ 30 s later (lead time),
   stores and online FC pull at the next period close and receive it after the carrier's 20 s.
6. **Admin forms from the catalog**: `AdminPageVM` gets `catalog` (`List<CatalogProductVM>` name + type);
   `admin.ts` keeps the supplier tabs and form actions but takes each form's products from the catalog by type
   (`SUPPLIER_BOXES` loses its hard-coded product lists).
7. **Config**: `inventory.dc-seed.enabled=true`, `inventory.dc-seed.quantity=500`, `%test....enabled=false`;
   `-Dinventory.dc-seed.enabled=false` in `playwright.config.ts`. (In `%test` and e2e the period close is off anyway,
   but the reset would seed.)
8. **Tests**: `SeedFlowTest` (switch on via a test profile or by calling `PurchasingHandler.seedDc` directly, like the
   other flow tests call the Handlers): an empty DC gets 28 `SEED` orders and, after the deliveries, 500 of each
   product; a second call orders nothing; a DC with stock or an open order is not seeded. A concurrency test: two
   parallel `seedDc` calls → 28 orders, not 56. `AdminReceiverTest`: the page view carries the catalog. e2e unchanged
   (forms show the same products).
9. **Docs**: `architecture-flow.md` (event section: `DcSeedReceiver`, reset tree), `architecture-module-participants.md`
   (`Catalog`, `DcSeedReceiver`, `PurchasingHandler.seedDc`, `openSeed`), `architecture-flow-kafka-reference.md`,
   `two-pods_wip.md` (the advisory lock), new `flows/dc-seed.puml` + `flows/README.md`, `README.md` (the demo starts by
   itself), session-notes baseline.
10. **Verification**: app-server suite, e2e (`--retries=0`); dev app after a reset: seed orders on `/admin`, then DC
    and store stock, and the store simulation selling.

Result 2026-10-05: done (staged). Core 161, external-inbound-kafka 5, app-server 158 (`DcSeedFlowTest` 7), e2e 26/26
(second run; the first after the rebuild failed 14, the known double live reload).
The dev check found a wrong assumption in the plan: `DemandPeriodReceiver` fired right at the start (no `delayed`),
so the seed ran before the HTTP server listened - the REST/SOAP orders were refused and cancelled, the 4 Kafka ones
went through and then blocked every later seed ("no open order at all"). Two fixes, approved by the user:
- **Seeding per product** instead of all-or-nothing: `openSeed` orders every catalog product the DC has no stock row
  and no open order *of*, so a cancelled seed order is ordered again at the next period close (also closes the
  "supplier down → not retried" gap of plan step 4). Tests: `a_product_the_dc_carries_is_not_seeded`,
  `a_product_on_order_is_not_seeded`, `a_cancelled_order_is_seeded_again`.
- **The first period close one period after the start** (`DemandPeriodReceiver`: `delayed =
  "${inventory.first-period-close-delay}"`, 1m - a property of its own, since `delayed` rejects the `off` of tests/e2e):
  no supplier call before the HTTP server listens, and no zero-length first period that learned a demand of 0 (an
  existing distortion).
Dev run after the fixes (fresh database): no seed at the start, `DC_SEEDED` at t+60 s without `DC_SEED_FAILED`, all
28 products at 500 in the DC by t+105 s. (That the stores then pull at the next close was not watched in the dev app; the flow tests cover it.)
- **Startup seed after 5 s** (follow-up, 2026-10-05): the DC stayed at 0 for ~1.5 min after a start, which looked
  broken. `DcSeedReceiver.onStart` (`StartupEvent` → one-off Vert.x timer, `inventory.dc-seed.startup-delay=5s`, the
  seed on a worker thread) seeds once the HTTP server listens; the period close keeps its 1 min delay.

## store-occupancy: Show the stores' occupancy in the UI, add/remove tills (DONE)

Added 2026-10-05 as the follow-up of `store-capacity`. The store simulation (capacity, till queue, lost customers)
lives in the cashpoint stub, so the app doesn't know it. Showing it would need the external system to report it, e.g.
door counters publishing a `store-occupancy` Kafka topic, plus a receiver, an event and a section on `/locations`.
To elaborate: whether it is worth it for the demo, message shape and rate, where it is shown. Next item: to be started in
a new session (decided 2026-10-05).

Elaborated 2026-10-05. Worth it mainly for what it teaches: every Kafka inbound so far is an *event* (process once);
occupancy is a *state snapshot* (only the latest counts). Keyed by store, it suits a compacted topic, and storing it is
idempotent (newer overwrites, stale is ignored), so it needs no inbox, not even with two pods. It is also the first data
the app shows without acting on it, and it makes the till bottleneck of `store-capacity` visible. The user added:
**+/- buttons per store to open or close a till**, to watch the effect on the queue.

Decisions: compaction only mentioned in the docs (no topic config); shown on `/locations` only; current values only, no
history; the till change goes through the app (option A: UI → core → outbound port → checkout system), not from the
browser straight to the stub.

### Plan (APPROVED 2026-10-05)

1. **Message** (topic `store-occupancy`, Kafka key = storeId): `{storeId, measuredAt, inside, queuing, tills,
   tillsBusy}` - current values only, so a redelivered or reordered message is harmless. The stub sends one per store
   every 5 s, and one right after a till change.
2. **Stub** (`external-inbound-kafka`):
   - `StoreSimulation.setTills(n)`: opening adds a free till; closing removes a free till, or - if all are busy - the
     next one that becomes free (the customer at it finishes paying). The arrival rate stays tied to the *configured*
     tills, otherwise opening a till would also bring more customers and the queue would not shrink.
   - `CashpointStub`: `occupancy-out` channel (key = storeId); a till change is queued (concurrent map) and applied by
     the next tick, since the simulation is not thread-safe.
   - `CashpointTillsStub`: `PUT /cashpoint-stub/stores/{id}/tills` `{"tills": n}` → 204; unknown store 404; n outside
     1..8 → 400.
3. **Core** (`cross.occupancy`):
   - `StoreOccupancy(Store, measuredAt, inside, queuing, tills, tillsBusy)` with `parse()` / sealed
     `ParsedStoreOccupancy` (counts ≥ 0, tills 1..`TillCount.MAX_TILLS`).
   - `TillCount(Store, tills)` with `parse()` / `ParsedTillCount` (1..8).
   - `OccupancyHandler`: `record(StoreOccupancy)` (fires `OccupancyChanged(store)` if it was newer), `current()`,
     `setTills(TillCount)` → `boolean` (false + audit log if the checkout system refused or was unreachable).
   - `OccupancyRepositorySPI` (`saveIfNewer`, `findAll`), `CheckoutSystemSPI` (`setTills`).
4. **Outbound**: `outbound-postgres` `StoreOccupancyTable` + `OccupancyService`, migration `V4__store_occupancy.sql`
   (one row per store; upsert `on conflict … do update … where excluded.measuredAt > store_occupancy.measuredAt`).
   `outbound-httpclient` `CheckoutSystemClient` + `CheckoutSystemService` (rest client `checkout-system`). The
   admin reset does not touch the occupancy: it is the external system's state.
5. **Inbound**: `inbound-kafka` `StoreOccupancyReceiver` + deserializer: same dead-letter handling as
   `CashpointReceiver`; no audit entry per message (36 per minute), only invalid ones.
6. **UI** (`/locations`): per store card a line "N inside · **N queuing for a till** · tills busy/total" with − / +
   buttons (`POST /locations/{id}/tills`, form field `tills`); "No occupancy data" without a snapshot, greyed out when the
   snapshot is older than 30 s. Refreshed on a per-store SSE event `occupancyChanged-{storeId}` on the existing
   `/inventory/events` stream; the inventory fragment is not re-rendered.
7. **Tests**: `StoreSimulationTest` (open/close a till, closing a busy till), flow test via Kafka (valid, stale ignored,
   invalid, unknown store), `LocationReceiverTest`-style tests for the fragment and the till POST (app → REST → stub),
   e2e: "No occupancy data" on `/locations`.
8. **Docs**: update-architecture-docs (topic, receiver, SPIs, Services, endpoints), compaction mentioned in
   `architecture-flow-kafka-reference.md`, `two-pods_wip.md` (snapshot consumer needs no inbox; the till REST call
   reaches one pod's stub), `concepts.md`, `README.md`, `docs/ai/session-notes.md`.

Implemented as planned (staged): `StoreSimulationTest` 10, app-server 167, e2e 27/27. With the capacity follow-up: `StoreSimulationTest` 14, app-server 167, e2e 27/27 (second run; the first after the change mass-failed, the known double live reload). Not yet watched in the dev app. Deviations: the occupancy
message channel is `store-occupancy-out`; the flow test first waits until the receiver consumes (a probe report,
harmless since only the latest counts) - right after the start the consumer is not yet assigned and skips what is
published before (`auto.offset.reset=latest`).

### Follow-up: capacity back, so that tills matter (APPROVED 2026-10-05)

The user expected more tills to bring more sales. They didn't: without a capacity nobody is turned away, every customer
who enters pays sooner or later, so over a demo day the sales equal the arrivals and more tills only shorten the queue.
Decided (user): reinstate the store capacity - a full store turns new customers away (lost sales), and opening tills is
the only means against it. The old capacities (60/40/20 at a peak of 1.5×) never bound; a simulation of the model
(20 demo days) chose a peak of **2.5×** and capacities **Zurich 16, Basel 10, Bern 6** (small, since a stay is only
1.25-2.5 s - most customers inside are queuing or paying):

| Store (tills) | Capacity | Configured tills: paid / turned away per demo minute | +1 till | +2 tills |
|---|---|---|---|---|
| Zurich (4) | 16 | 15.4 / 7.3 | 19.4 / 3.5 | 21.4 / 0.8 |
| Basel (2) | 10 | 7.7 / 3.9 | 11.2 / 0.5 | 11.7 / 0.1 |
| Bern (1) | 6 | 3.8 / 1.5 | 5.5 / 0.1 | 5.5 / 0.1 |

The arrivals stay tied to the configured tills (the store's demand). The report gets `capacity` and `turnedAway`
(customers turned away in the last demo day - a current value, not a delta, so the snapshot stays idempotent), via
`V5__store_occupancy_capacity.sql` (V4 is already applied in the dev database). The line on `/locations`: "14 / 16
inside" (+ "full") · queuing · tills busy/open · − / + · "N turned away (last minute)".

## plain-sql: Replace JPA (Hibernate/Panache) with plain SQL in outbound-postgres (DONE)

Added 2026-10-04 at the user's request. The change stays inside `outbound-postgres` (~725 lines: 4 entities, 3 Services)
plus the test helper `TestInventoryHelper`; core and the SPIs don't change - the hexagon at work. Flyway stays and
owns the schema either way.

Assessment (2026-10-04) - no real disadvantage for this demo, a few costs:
- **More code for writes.** Today the Services change entity fields (`dcStock.availableAmount -= quantity`) and
  Hibernate's dirty checking writes them on commit. With SQL every change is an explicit `UPDATE`. That is more lines,
  but also clearer: what is written, and when, is visible in the code.
- **Row mapping by hand** (`ResultSet` → record) for 4 tables. Small, and it can map straight to core's records
  (`LocationStock`, `ReplenishmentRequest`, `Shipment`, `SupplierOrder`), so the entity → `toDomain()` step goes away.
- **SQL in strings is not checked at build time.** A typo shows up at runtime; the flow tests cover every query, so
  it would show up in the build, not in production. Today's JPQL strings are no better checked.
- **No schema check at startup**: Hibernate's `database.generation=validate` goes away. The flow tests against the
  Flyway schema take that role.
- **Ids**: Panache's pooled sequences (`*_SEQ`, step 50) become `bigint generated always as identity` with
  `INSERT … RETURNING id` (a new migration).

Gains: locking becomes explicit (`SELECT … FOR UPDATE`, possibly `SKIP LOCKED`), the Hibernate workarounds go away
(e.g. `productNameOf` "without loading, so it can be locked afterwards", first-level cache surprises), and the demo
shows one more persistence style. `@Transactional` keeps working with the Agroal datasource.

Decided 2026-10-05: plain JDBC with a small helper.

### Plan (APPROVED and done 2026-10-05)

1. **Dependencies** (`outbound-postgres/pom.xml`): drop `quarkus-hibernate-orm-panache`; add `quarkus-agroal` and
   `quarkus-narayana-jta` explicitly (today both come in through Hibernate; `@Transactional` and
   `QuarkusTransaction` need the latter). Agroal enlists its connections in the JTA transaction, so all statements
   of one `@Transactional` method share one connection and commit together - nothing changes for the callers.
2. **`Db` helper** (`cross.jdbc.Db`, `@ApplicationScoped`, injects the `AgroalDataSource`, ~50 lines):
   `query(sql, mapper, params...)` → `List<T>`, `queryOne(...)` → `Optional<T>`, `update(sql, params...)` → row
   count, `insert(sql, params...)` → id (`INSERT … RETURNING id`). Parameter binding turns enums into `name()` and
   `Instant` into `OffsetDateTime`; `SQLException` is wrapped in an unchecked exception. One connection per call
   (inside a transaction it is the transaction's connection).
3. **Migration `V2__identity_ids.sql`**: every `id` becomes `generated always as identity`, starting above the old
   sequence's last value + 50 (the pooled optimizer may have handed out ids up to there, and Kafka messages in flight
   may still refer to them), then the four `*_SEQ` sequences are dropped. A reset still never reuses ids.
4. **The `*Entity` classes are replaced, one table per step** (flow tests after each step):
   - `ReplenishmentRequestEntity` and `SupplierOrderEntity` → `ReplenishmentRequestTable` and `SupplierOrderTable`
     (beans holding the SQL of their table), rows mapped straight to core's `ReplenishmentRequest` /
     `SupplierOrder` records.
   - `ShipmentEntity` → `ShipmentTable`; core's `Shipment` has no product `type`, which `receiveShipment` needs, so
     it maps to a small package-private `ShipmentRow` record (`toDomain()`).
   - `StockEntity` → `StockTable` + `StockRow` record (id, demand and level columns are adapter internals, not in
     core's `Product`/`LocationStock`); `levels()`, `estimate()`, `toDomain()`, `toLocationStock()` move to the row.
   - Writes become explicit, relative where possible (`UPDATE stock SET availableAmount = availableAmount + ? WHERE
     id = ?`), so no in-memory copy can go stale. Where a loop needs the running value (`allocate` sharing the DC
     stock), the Service keeps it in a local variable.
   - Locking: `SELECT … FOR UPDATE` in exactly today's order (DC row, then requests/orders oldest first, then the
     target row). `productNameOf` stays (the lock order needs the product before the lock) but loses its Hibernate
     cache comment.
   - `ResetService`: four `DELETE`s via `Db`.
5. **Tests**: `TestInventoryHelper` uses `Db` instead of the `EntityManager`; no other test touches JPA (the
   `TestAuditLogHelper` is Mongo Panache and stays).
6. **Config/docs**: `quarkus.hibernate-orm.database.generation=validate` and its comment go (the flow tests against
   the Flyway schema take over that check); the V1 header comment notes that Hibernate is gone; README tech table,
   `architecture-module-participants.md`, `architecture-flow-kafka-reference.md`, `architecture-flow.md`,
   `two-pods_wip.md` (schema section) get the new class names and "plain JDBC"; `docs/ai/session-notes.md` baseline.
7. **Verification**: full `app-server` test suite and e2e (`--retries=0`); then the dev app (data from before V2
   survives the migration).

Result: 150 app-server tests green, e2e 25/25 (second run; the first after the rebuild failed 14, the known double
live reload). Deviations: plain absolute writes on locked rows (`shipped`, `status`, `delivered`) instead of relative
ones - the row lock already rules out a stale value; only the stock amount is changed relatively (`addAvailable`), so
the DC row snapshot inside one allocation needs no refresh. `request()` reads the new request again at the end, since
the allocation may ship to it (Hibernate returned the same managed instance). V1 is untouched: editing even its
comments changes Flyway's checksum and fails existing databases.

## admin-reset: Admin "Reset demo data" button (DONE)

Done 2026-10-05. Since Flyway, the dev data survives restarts, so `/admin` got a *Reset demo data* button (shell
header, `hx-confirm`) → `POST /admin/reset` → core `ResetHandler.reset()`: `ResetRepositorySPI.deleteAll()` (new
`ResetService` in outbound-postgres deletes stock, replenishment_request, shipment and supplier_order in one
transaction; the sequences stay, so ids remain unique), `AuditLogSPI.clear()` (the audit log is cleared, too), audit
`INVENTORY_RESET`, then the new `InventoryReset` event refreshes every page via SSE. Messages in flight are harmless: a
late supplier delivery adds to the DC, a late shipment arrival is ignored. Tests: `AdminReceiverTest` case; the e2e
test intercepts the POST (a real reset would wipe the data of the spec files running in parallel).

## faster-tests: Shorten the test runs (TO ELABORATE)

Added 2026-10-05 at the user's request: the test runs take too long. Measured on 2026-10-05 (`store-occupancy`):
- **app-server: 3:48 min** for 167 tests (+ build). The 7 slowest classes are exactly the 7 with a `@TestProfile`
  (`KafkaTransientFailureTest` 32 s, `KafkaMalformedMessageTest` 21 s, `SupplierLeadTimeFlowTest` 21 s,
  `AutoReplenishmentFlowTest` 20 s, `DcSeedFlowTest` 18 s, `AutoPurchasingFlowTest` 18 s, `ShipmentTransitFlowTest`
  17 s) - each profile restarts Quarkus (Kafka/Postgres/Mongo dev services, Flyway clean). Together ≈ 2.5 min of the
  run; the other 26 classes share one app.
- **e2e: ≈ 50 s** for 27 tests, plus the dev-server start; the first run after a code change can mass-fail (double live
  reload, see memory) and costs a second run.
- Many flow tests wait on Kafka round trips with `await().atMost(10, SECONDS)`.

To elaborate - candidate levers, cheapest first:
1. **Fewer Quarkus restarts:** merge profiles that only differ in config values that could be switched at runtime
   (e.g. the period timer / lead time / transit time via a test-only setter or a config the test reads), or group the
   profiled classes so ones with the same profile share an app.
2. **Run only what a change touches** during development (`-Dtest=…`, a fast "unit + affected flow tests" set) and the
   full suite before staging; the stub's plain unit tests already run in < 1 s.
3. **Shorter waits:** Awaitility poll interval / Kafka consumer `fetch.max.wait.ms` and `auto.commit` settings in
   `%test`, consumer group rebalance delay (`group.initial.rebalance.delay.ms` of the dev-services broker).
4. **Parallelism:** surefire forks per profile, or e2e workers - limited by the shared databases.
5. **e2e:** avoid the double live reload (start the e2e dev server after the build has settled, or run e2e against a
   packaged jar instead of `quarkus:dev`).

## Open questions

- Authentication/authorization is out of scope for this POC, but the separate routes (`/admin`, `/shop`) make it easy to add later.
