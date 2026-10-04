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

## diagram-left-to-right: Diagram left-to-right flow improvement (NOT STARTED)

Reorganize all architecture diagrams (main + 4 focused ones) to ensure **strict left-to-right dependency flow through core**:
- External sources / inbound → **Core** → outbound adapters → external systems
- No arrows crossing the core horizontally
- Visual clarity: where does data/requests come in, where do they go out

This is deferred because PlantUML's auto-layout makes it challenging to enforce; a manual coordinate-based approach or a different diagram tool might be needed for full control.

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

## split-inventory: Locations: central DC, 3 stores, online dark store (PLAN)

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
implement it for now (2026-10-04). Analysis and suggested order:
[`docs/architecture/two-pods_wip.md`](docs/architecture/two-pods_wip.md).

## Open questions

- Authentication/authorization is out of scope for this POC, but the separate routes (`/admin`, `/shop`) make it easy to add later.
- `diagram-left-to-right` may need a different tool or manual layout if PlantUML cannot enforce the strict left-to-right constraint.
