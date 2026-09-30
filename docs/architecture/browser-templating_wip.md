# Browser-side templating (hono/html) — Plan & Work In Progress

Replaces Qute in `inbound-http-html` with the approach from
[svene/2026-09-03_hypermedia-quarkus-browser-hono](https://github.com/svene/2026-09-03_hypermedia-quarkus-browser-hono):
the server returns a JSON `{ route, vm }` envelope, and a small htmx 4 extension (`hono`) renders the
matching hono/html template **in the browser** before htmx swaps it in. Tracked as `PLAN.md` § 9.

Status: **NOT STARTED** — plan only, not yet approved for implementation.

## Current state (what gets replaced)

| Qute template (`inbound-http-html/src/main/resources/templates/`) | Served by | Used for |
|---|---|---|
| `AdminReceiver/admin.html` | `GET /admin` | full page: inventory table, 7 order forms, audit panel |
| `AdminReceiver/inventoryFragment.html` | `GET /admin/inventory-fragment` | polled `<tbody id="inventory-body">` |
| `AdminReceiver/auditFragment.html` | `GET /admin/audit-fragment` | polled `#audit-panel` |
| `AdminReceiver/orderErrors.html` | `POST /admin/order-*` (400) | violation messages below a form |
| `ShopReceiver/shop.html` | `GET /shop`, `POST /shop/checkout` (400) | full page with cart rows / errors |
| `ShopReceiver/inventoryFragment.html` | `GET /shop/inventory-fragment` | `<hx-partial id="avail-…">` updates |

`AdminReceiver`/`ShopReceiver` use `@CheckedTemplate` and hand core types (`Product`,
`AuditLogEntry`) straight to the templates. Dependency: `quarkus-rest-qute` in
`inbound-http-html/pom.xml`. htmx 4.0.0 is already vendored.

## Target shape (from the reference repo)

- **Static shells** `admin.html` / `shop.html` under `META-INF/resources/` (Bulma, htmx 4,
  `hx-hono.js` loaded right after `htmx.js`), each with an `#app` element doing
  `hx-get="/…/uiroute/<Page>" hx-trigger="load"`.
- **`UiResponse(String route, Object vm)`** JSON envelope returned by every fragment/page GET and by
  the 400 error responses.
- **View-model records** (`*Model`/`*VM`) owned by `inbound-http-html` — not core's `Product` /
  `AuditLogEntry`, so the TS types are generated from the adapter's own records and core stays
  free of UI concerns.
- **Route-name enum** (e.g. `AdminRouteName`, `ShopRouteName`) → TS string-union via
  `typescript-generator`; `routes.ts` uses `satisfies Record<RouteName, RouteDefinition>` so a
  missing template is a TS compile error.
- **Mutation endpoints** (`POST /admin/order-*`, `POST /shop/checkout`) stay on their own paths; on
  success they return `204` (+ `HX-Trigger` where a refresh is wanted), on validation failure a
  `400` `{ route: "OrderErrors", vm }` envelope.
- `.ts` templates live next to the receivers in `src/main/java/…/cross/` (web tier, not a separate
  frontend project), bundled by esbuild into `META-INF/resources/js/hono/hx-hono.js`.

## Steps

1. **Spike / decisions** (see open questions) — build integration and the no-JS fallback.
2. **Build tooling**: `package.json` (esbuild + hono), `tsconfig.json`, `typescript-generator` +
   gmavenplus (only if runtime path constants are needed) in `inbound-http-html/pom.xml`;
   generated files git-ignored.
3. **VM records + route enums** in `inbound-http-html`; mapping from core types lives in the
   receivers.
4. **Admin page**: shell, `Page`, `InventoryTable`, `AuditPanel`, `OrderErrors` templates; switch
   `AdminReceiver` to `UiResponse`. Keep the Randomize (dev) button working (`main.js`,
   `htmx.trigger(form, 'submit')`).
5. **Shop page**: shell, `Page`, cart rows, availability cells, checkout errors; switch
   `ShopReceiver`. `ShopCart` stays as the form→`Purchase` parsing helper.
6. **Remove Qute**: delete `templates/`, drop `quarkus-rest-qute`; add JSON provider if not already
   on the module's classpath.
7. **Tests**: Quarkus receiver tests assert JSON envelopes instead of HTML; Playwright
   (`admin.spec.ts`, `shop.spec.ts`) must pass unchanged in behavior (selectors like
   `#inventory-body` preserved).
8. **Docs**: `README.md`, `docs/architecture/*` (flows, module participants), `docs/ai/session-notes.md`
   baseline — via the `update-architecture-docs` skill.
9. Optional: dev live-reload (`watch.ts`, `JsBundleWatcher`, `DevReloadSSE`) as in the reference.

## Open questions / decisions needed

- **Bundle build**: the reference repo builds `hx-hono.js` outside Maven (`npm run build` / Bun
  watcher). Here Quarkus tests and Playwright need the bundle, so either (a) run esbuild from Maven
  (`frontend-maven-plugin` or `exec-maven-plugin`), or (b) commit the bundle. Recommendation: (a).
- **No-JS fallback**: `AdminReceiver` currently answers plain form posts with `303` and htmx posts
  with `204`. With browser-rendered templates a page without JS renders nothing, so the plain-form
  branch loses its purpose. Recommendation: drop it and accept "JS required" (it's a demo).
- **First-paint flash**: the shell renders empty until `/uiroute/Page` returns — acceptable per the
  reference repo; a server-inlined bootstrap VM would be a follow-up.
- **Interaction with § 10 (live updates)**: `hx-multipart` swaps each part via `htmx.swap()`
  directly, **bypassing `htmx_after_request`**, so the `hono` extension as written will not render
  JSON parts. It needs a second hook (e.g. on `htmx:multipart:before:part`, replacing the part's
  text with the rendered HTML) — verify in a spike. Recommendation: do § 9 first, so § 10 is built
  on JSON parts from the start rather than converted twice.
- Alpine.js (`js/alpinejs/3.15.4`) is vendored but appears unused — remove as part of this, or
  leave it to § 10 (hx-live)?

## Progress log

_(append dated entries as steps land)_
