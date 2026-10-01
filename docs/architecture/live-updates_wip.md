# Live updates: server-push multipart stream + hx-live — Plan & Work In Progress

Replaces the 3-second htmx polling on `/admin` and `/shop` with a long-lived `multipart/mixed`
stream pushed by the server, following
[svene/2026-08-02_quarkus-multistream-mixed-response](https://github.com/svene/2026-08-02_quarkus-multistream-mixed-response),
and uses htmx 4's [`hx-live`](https://four.htmx.org/extensions/hx-live) extension for the
client-side reactive bits. Tracked as `PLAN.md` `live-updates`.

Status: **NOT STARTED** — plan only, not yet approved for implementation.

## Current state (what gets replaced)

- `/admin`: `<tbody id="inventory-body" hx-get="/admin/inventory-fragment" hx-trigger="every 3s"
  hx-swap="outerHTML">` and `<div id="audit-panel" hx-get="/admin/audit-fragment" hx-trigger="every 3s">`
  — each fragment re-renders itself including the polling attributes.
- `/shop`: `<div id="shop-products" hx-get="/shop/inventory-fragment" hx-trigger="every 3s"
  hx-swap="innerMorph">` re-renders the whole products section (empty state or cart form) and morphs
  it in. Rows are keyed `row-{name}-{type}` and the quantity inputs carry no `value` attribute, so a
  customer's in-progress quantities and focus survive (since `shop-product-set-refresh`).
- Every open page issues a request every 3 s whether or not anything changed; changes show up with
  up to 3 s delay.

## Two separate pieces

**Server push (the reference repo's approach).** A `GET` endpoint `@Produces("multipart/mixed;
boundary=…")` returning `Multi<byte[]>`; RESTEasy Reactive flushes each part immediately (chunked,
no `Content-Length`). htmx 4's `hx-multipart` extension swaps each part as it arrives. For a
long-lived connection the extension offers `hx-multipart:connect="<url>"` with automatic
reconnect (exponential backoff + jitter, `HX-Last-Part-ID`), and per-part headers
(`HX-Target`/`HX-Retarget`, `HX-Swap`/`HX-Reswap`, `HX-Trigger`, `HX-Part-ID`) — so one stream can
update several regions of the page.

**`hx-live` (client-side reactivity).** Not a transport — it binds DOM state with inline
expressions (`:disabled="…"`, `:text="…"`, `:.is-danger="…"`, `q('#id')`) and recomputes on DOM
mutations (including attribute/text changes) and `input`/`change` events. Because pushed parts
mutate the DOM, `hx-live` bindings react to them automatically.

## Core idea: the stream feeds a hidden data island, hx-live renders from it

The stream does **not** push visible UI fragments. It pushes a small HTML snippet into a hidden
**data island** at the top of the page — the server-pushed equivalent of an Alpine.js `x-data`
object. The visible page reads from it through `hx-live` bindings:

```html
<body>
  <!-- data island: replaced (innerHTML) by every stream part; never shown -->
  <div id="live-data" hidden hx-multipart:connect="/shop/live" hx-target="#live-data" hx-swap="innerHTML">
    <data id="stock-apple"  value="12"></data>
    <data id="stock-banana" value="0"></data>
    …
  </div>

  <!-- visible UI binds to it -->
  <td :text="q('#stock-apple').value"></td>
  <tr :.is-danger="+q('#qty-apple').value > +q('#stock-apple').value">…</tr>
  <button :disabled="…any row over stock…">Checkout</button>
</body>
```

Consequences:
- The stream never touches the visible markup, so a customer's in-progress quantity inputs are safe
  by construction (today this needs the `hx-partial`/OOB trick).
- Each part is simply the full current snapshot of the island — no diffing, no per-region
  targeting, and a (re)connect is just "send the snapshot again".
- Derived UI (stock warnings, disabled buttons, cart totals) lives in `hx-live` expressions next to
  the elements they affect, not in a JS file — which also covers what Alpine.js was vendored for.

Candidate bindings:
- `/shop`: "Available" cells; flag a cart row (`:.is-danger`) and disable *Checkout* when a
  requested quantity exceeds live stock; live cart item count.
- `/admin`: inventory table amounts; disable an *Order* button while its quantity input is empty /
  out of range.

**The audit log does not fit this model**: it is an append-only list of entries rendered as rows,
not a fixed set of values bound into existing markup. Decided 2026-09-30: it **keeps its
`every 3s` polling** (`GET /admin/audit-fragment`) for now.

### When the set of products changes

Bindings attach to rows that already exist, so a product appearing for the first time (new product
name, or on `/shop` a product going from 0 back into stock) or disappearing has no row to bind
to. Decided 2026-09-30: the stream **sends a part that makes that area reload itself**.

- The server tracks, per connection, the product set of the last snapshot it sent (on `/shop`: the
  in-stock set). If the set changed, the snapshot part carries an extra part header
  `HX-Trigger: productSetChanged`.
- The trigger rides on the snapshot part rather than on a separate, empty part: `hx-multipart`
  still swaps a part's body after running `HX-Trigger`, so an empty part would wipe the island.
- The area listens and re-fetches itself once, e.g.
  `<tbody id="inventory-body" hx-get="/admin/inventory-fragment" hx-trigger="productSetChanged from:body">`.
  The event is dispatched on the island and bubbles to `body`. The fragment endpoints therefore
  stay, but are fetched on demand, not polled.
- `/shop` caveat: reloading the cart rows must not lose the customer's typed quantities. Use a
  morph swap (`innerMorph`/`outerMorph` in htmx 4, rows and inputs keyed by stable ids) so
  existing inputs keep their values; verify in the spike.

## Target shape

- `/shop`: `#live-data` island with `hx-multipart:connect="/shop/live"`; one `<data>` per product.
- `/admin`: `#live-data` island with `hx-multipart:connect="/admin/live"` for the inventory
  amounts; the audit panel keeps its 3 s polling.
- The inventory `hx-trigger="every 3s"` attributes are removed; the inventory fragment endpoints
  remain as reload targets for `productSetChanged`.
- Vendored `js/htmx.org/4.0.0/ext/hx-multipart.js` and `ext/hx-live.js` (4.0.0, matching core —
  the reference repo used 4.0.0-beta6).

## Steps

1. **Change-notification source** (see open questions) — how the HTTP adapter learns that
   inventory changed (the audit log is out of scope, it stays polled).
2. **Broadcaster** in `inbound-http-html`: a Mutiny `BroadcastProcessor` (or similar) that each
   open stream subscribes to; per-connection `Multi` = initial island snapshot + a new snapshot
   per change + periodic heartbeat, then closing boundary on cancel.
3. **Spike on `/shop`**: `/shop/live` endpoint, `#live-data` island, `hx-live` bindings for the
   "Available" cells; confirm that an `innerHTML` swap into a `hidden` element re-triggers the
   bindings. Remove shop polling; `/shop/inventory-fragment` (already a morph swap that keeps
   typed quantities) becomes the reload target for `productSetChanged`.
4. **Derived shop UI** via `hx-live`: over-stock row flag, Checkout disabled, cart count.
5. **`/admin`**: `/admin/live` island for inventory amounts + bindings; order-button bindings;
   remove the inventory polling, `productSetChanged` reloads `#inventory-body`. Audit panel
   unchanged (still polled).
6. **Tests**: a streaming test like the reference repo's `MultipartMixedResourceStreamingTest`
   (no `Content-Length`, part N arrives after a triggered change, not buffered);
   Playwright: other-tab/Kafka change appears without reload; review any `networkidle` waits,
   which never settle on a page holding an open stream.
7. **Docs**: `README.md`, `docs/architecture/*` (new flows for live updates), `docs/ai/session-notes.md`
   baseline — via `update-architecture-docs`.

## Open questions / decisions needed

- **Where do change notifications come from?** Inventory changes via Kafka deliveries/cashpoint,
  JSON API, admin orders and shop checkout — all through core handlers into `InventoryRepositorySPI`;
  audit entries via `AuditLogSPI`. Options:
  (a) new core SPI (e.g. `InventoryChangeNotifierSPI`) called by handlers after commit, implemented
  by the broadcaster — explicit, hexagon-conform, single-instance only;
  (b) CDI event `@Observes(during = AFTER_SUCCESS)` — less code, but an implicit coupling the
  ArchUnit rules don't see;
  (c) Postgres `LISTEN/NOTIFY` / Mongo change streams — multi-instance-safe but heavier.
  Recommendation: (a).
- ~~Diff vs. snapshot parts~~ — decided 2026-09-30: every part is the full data-island snapshot;
  a (re)connect just gets a fresh snapshot (`HX-Last-Part-ID` ignored).
- ~~Audit log~~ — decided 2026-09-30: keeps polling for now.
- ~~Row set changes~~ — decided 2026-09-30: `HX-Trigger: productSetChanged` on the snapshot part,
  the area re-fetches itself (see "When the set of products changes").
- **Dependency on `browser-templating`**: if `browser-templating` lands first, parts are `{ route, vm }`
  JSON and the `hono` extension needs a hook for multipart parts, since `hx-multipart` calls
  `htmx.swap()` directly and bypasses `htmx_after_request` (see `browser-templating_wip.md`).
  If `live-updates` lands first, parts are Qute-rendered HTML and get converted later. The data-island
  design softens this: the island is trivial markup (`<data>` elements), so the stream could
  build it without any template engine and bypass the `hono` extension entirely.
- Connection limits: browsers allow ~6 HTTP/1.1 connections per origin; two tabs × one stream is
  fine, but note it in the docs. Quarkus dev-mode live reload must close open streams cleanly.

## Progress log

_(append dated entries as steps land)_
