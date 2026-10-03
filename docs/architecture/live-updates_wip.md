# Live updates: `/shop` refreshes on a server-sent event — Design & Decisions

Replaced the 3-second htmx polling on `/shop` with a server push: the server sends an SSE event
whenever the inventory changes, and the page re-fetches its products fragment. Tracked as
`PLAN.md` `live-updates`.

Status: **DONE** (2026-10-01).

## How it works

```
shells/shop.html   <div hx-sse:connect="/inventory/events" hidden>    (outside #app; same in shells/admin.html)
shop.ts            <div id="shop-products" hx-get="/shop/inventory-fragment"
                        hx-trigger="inventoryChanged from:body" hx-sync="this:replace" hx-swap="innerMorph">
```

- **Server → browser.** `InventoryEventsReceiver.events()` (`GET /inventory/events`, `text/event-stream`, a
  `Multi<OutboundSseEvent>`) merges three streams: one `inventoryChanged` on connect, one per
  inventory change, and a `: heartbeat` comment every 15 s (lets the server notice closed
  connections).
- **Browser.** htmx 4's `hx-sse` extension (vendored as `js/htmx.org/4.0.0/ext/hx-sse.js`)
  dispatches a *named* SSE event as a DOM event on the connecting element; it bubbles to `body`,
  where `#shop-products` listens. The re-fetch goes through the normal htmx/`hono` path and is morphed
  in, so rows keep typed quantities and focus (see `shop-product-set-refresh`). `hx-sync="this:replace"`
  collapses a burst of events (e.g. several deliveries arriving close together) into the latest request.
- **Reconnect.** `hx-sse` reconnects automatically (backoff + jitter) and disconnects while the tab
  is in the background. Because the server sends `inventoryChanged` on every (re)connect, changes
  made while disconnected are picked up.
- **Where the connection lives.** In the shell, outside `#app`: a checkout re-renders `#app` and
  must not drop the stream.
- **Script order matters.** `hx-sse.js` loads after `hx-hono.js`: the `hono` extension *sets*
  `Accept: application/json, text/html;q=0.9`, and `sse` *appends* `, text/event-stream`. Loaded the
  other way round, the stream request would be refused with 406.

## Change notifications (core)

**Superseded 2026-10-03 (split-inventory):** `InventoryChangesHandler` was replaced by the CDI event type `InventoryEvent` (fired with `fireAsync`), observed by `InventoryEventBroadcaster` in inbound-http-html; see `architecture-flow.md`, GET /inventory/events. As originally built:

`core` `cross.inventory.InventoryChangesHandler`:
- `publishChange()` — called by `InventoryHandler.update*Amount()` (every delivery) and by
  `PurchaseHandler.deduct()` when something was deducted (shop/JSON API checkout, cashpoint). The
  `@Transactional` boundaries are on `InventoryService`'s own methods, so the change is committed
  when the Handler publishes, and a re-fetch sees it.
- `changes()` — a JDK `Flow.Publisher` (`SubmissionPublisher`); `InventoryEventsReceiver` wraps it with
  `Multi.createFrom().publisher(...)`. Core stays free of reactive libraries.
- Non-blocking: a subscriber with a full buffer misses an event instead of stalling a delivery or
  checkout.
- **Not an SPI**: `ArchitectureTest.inbound_adapters_do_not_use_spis` forbids an inbound adapter
  implementing one, and notifications flow core → inbound adapter, which reaches core through
  Handlers.
- **In-process only**: with several app instances a browser would miss changes made on another
  instance (would need e.g. Postgres `LISTEN/NOTIFY`).

## Decisions (2026-10-01)

- **`/admin` keeps polling.** Its inventory table has no user input to protect (a 3 s `outerHTML`
  swap loses nothing and handles new/removed rows), the audit panel is polled anyway, and it's a
  single-operator page. Adding the stream there is one attribute if ever wanted.
  *Superseded 2026-10-02 (`PLAN.md` `admin-live-inventory`):* the admin inventory table got per-row
  *Restock* inputs, so it now uses the same stream (moved from `GET /shop/events` to the shared
  `GET /inventory/events`) and morphs `#admin-inventory`; only the audit panel still polls.
- **Event + re-fetch instead of pushing data.** Options weighed:
  (A) multipart stream into a hidden `<data>` island + `hx-live` bindings (the 2026-09-30 plan) —
  most machinery, and it still needed a re-fetch for product-set changes;
  (B) multipart stream pushing the rendered fragment — needs a hook in `hx-hono.ts`, because
  `hx-multipart` calls `htmx.swap()` directly and bypasses `htmx_after_request`;
  (C) SSE event + re-fetch — chosen: smallest change, standard SSE support in Quarkus and htmx 4,
  the stream is independent of how pages render. Cost: one extra GET per change per open page.
- **`hx-live`** is no longer needed for live updates; its client-side uses moved to `PLAN.md`
  `hx-live-ui`.

## Tests

- `InventoryEventsReceiverTest.events_stream_sends_inventory_changed_on_connect_and_after_a_purchase` — event
  on connect, and a second one only after a checkout (proves pushing, not buffering).
- Playwright `shop.spec.ts` "an open shop page picks up a newly stocked product without a reload
  and keeps typed quantities" — with polling gone, this only passes through the SSE push.
