# Two pods: running the app without downtime — what it would take

The demo is developed as if it ran in Kubernetes with **two pods** of the app behind one Service, so a rolling update
or a crashed pod causes no downtime. It does not actually run that way, and the changes below are **not implemented**.
This document lists what would have to change and in what order. Tracked as `PLAN.md` `two-pods`.

Status: **ANALYSIS ONLY** (2026-10-04). The user decided against code changes for now.

Background: the question was whether the CDI `fireAsync` / `@ObservesAsync` events should go through a Kafka topic the
app sends to itself (former plan item `kafka-internal-events`). Answering it showed that two pods affect more than the
events. Kafka is the right answer for one part (section 2) and the wrong one for another (section 3).

## What already works with two pods

- **Concurrency on shared stock.** All stock changes lock their Postgres rows (`SELECT … FOR UPDATE`, one transaction per
  use case in `outbound-postgres`). That works across processes just as across threads, e.g. two pods calling
  `orderIfLow` at once create one supplier order (`AutoPurchasingFlowTest.concurrent_checks_create_one_supplier_order`
  tests it with threads). Seeding the DC has no row to lock yet, so it takes a Postgres advisory lock
  (`SupplierOrderService.openSeed`, `DcSeedFlowTest.concurrent_seeds_order_once`). So the startup seed
  (`DcSeedReceiver.onStart`, a one-off timer 5 s after the start) may run in every pod, unlike the period close
  (section 1): the second pod - or a pod restarted in a rolling update - finds the first one's orders and seeds nothing.
- **No domain state in memory.** Locations are a fixed seed in core; stock, requests, supplier orders and levels live in
  Postgres, and the audit log in MongoDB. The HTML pages and the JSON API are stateless, with no sessions or auth.
- **Kafka work is shared.** All inbound channels use the default consumer group (`quarkus.application.name`), so the two
  pods share each topic's partitions instead of both processing every message.
- **Browsers reconnect by themselves.** `GET /inventory/events` sends an `inventoryChanged` on every connect, so a
  browser whose pod went away reconnects (via the Service, to the other pod) and refreshes its fragment.

## 1. Single-instance timer: the demand period (must do)

`DemandPeriodReceiver` (`@Scheduled(every = "${inventory.demand-period}")`) runs in **every** pod. With two pods every
period is closed twice: the learned averages, variances and levels are updated twice per period (wrong levels), and the
replenishment / supplier-order checks run twice (harmless thanks to the row locks, but wasted).
`ConcurrentExecution.SKIP` only protects against overlap inside one JVM.

What to do:
- Run the job in exactly one pod: `quarkus-quartz` in clustered mode (`quarkus.quartz.clustered=true`, JDBC job store in
  Postgres, Quartz tables via migrations). Quartz takes a database lock per firing, so only one pod runs a given period,
  and the other pod takes over if this one dies.
- Alternative without Quartz: keep `@Scheduled` and guard `closePeriod()` with a database lock
  (`pg_try_advisory_xact_lock`) plus a "last closed period" row, so a second run of the same period does nothing.
  Fewer moving parts, but hand-written.
- Either way, `closePeriod()` should become **idempotent per period** (store the period id in which a row was last
  closed), so that a retried or duplicated run cannot learn twice.

## 2. Live updates to every browser: cross-pod fan-out (must do, Kafka)

`InventoryEventBroadcaster` (`@ObservesAsync InventoryEvent` → `SubmissionPublisher` → SSE) only sees events fired in
**its own** pod. With two pods, a browser connected to pod A does not see a store sale that pod B's Kafka consumer
processed, a delivery that pod B received, or a request that head office fulfilled on pod B. `/shop`, `/admin` and
`/locations` would update only for about half of the changes.

What to do:
- A topic `inventory-changes`. An outbound adapter observes `InventoryEvent` (`@ObservesAsync`, or `TransactionPhase.
  AFTER_SUCCESS` if the event is fired inside a transaction) and publishes it. Core keeps firing CDI events and does not
  change.
- `InventoryEventBroadcaster` consumes the topic instead of the CDI event, with **its own consumer group per pod**
  (e.g. `inventory-changes-${HOSTNAME}`), `auto.offset.reset=latest` and no need to commit, so every pod gets every
  event. The payload is the event type and location, nothing more: the browser only uses it as a trigger to re-fetch.
- Losing an event here is acceptable: the next change or a reconnect refreshes the page.
- Alternative: Postgres `LISTEN/NOTIFY`. `pg_notify` in the same transaction is delivered only on commit, so there is no
  dual-write problem, and no extra infrastructure. But this demo shows technologies per adapter, and Kafka is already in
  the stack, so Kafka fits the demo better.

## 3. Work triggered by CDI events: keep CDI, add a catch-up (should do)

These events start work in the pod that made the change:

| Event | Observer | Work |
|---|---|---|
| `DeliveredToDc` | `DeliveryEventReceiver` | `fulfilPending` (serve pending requests from the delivery) |
| `StockDeducted` | `AutoReplenishmentReceiver` | `replenishIfLow` (store / online FC requests from the DC) |
| `DcDemandChanged` | `AutoPurchasingReceiver` | `orderIfLow` (DC orders from the supplier) |
| `LevelsRecalculated` | both receivers above | `replenishAllIfLow`, `orderIfLow` per DC product |

Two pods are not a problem for these in themselves: the work runs in whichever pod made the change, and the row locks
prevent duplicates. The problem is **losing an event**: `fireAsync` runs after the commit, in memory. A pod that stops
between the commit and the observer (SIGTERM in a rolling update, OOM kill) loses it. With zero-downtime rolling
updates this happens regularly, not just in theory.

Moving these events to Kafka does **not** fix this on its own. Commit to Postgres, then publish to Kafka is still two
writes, and a crash in between loses the event the same way. A real fix needs a **transactional outbox**: write the
event to an `outbox` table in the same transaction as the stock change, then a relay (a polling job or Debezium CDC)
publishes it, and consumers are idempotent. That is a big step.

Recommended instead: make the system **catch up from the current state** instead of depending on every event.
- The period close already does this for two of the three: `LevelsRecalculated` triggers `replenishAllIfLow` and
  `orderIfLow` for every product, so a lost `StockDeducted` or `DcDemandChanged` is repaired within one period (1 min).
- Missing: a catch-up for `DeliveredToDc`. Pending requests are only served when the next delivery of that product
  arrives. Add "allocate pending requests of every product that has DC stock" to the period close. A few lines in
  `ReplenishmentHandler` / `ReplenishmentService`; `allocate` is already safe to call repeatedly.
- The CDI events stay as the fast path, and the period close as the safety net.
- The transactional outbox stays an optional, separate showcase item, if the demo should show it explicitly.

UI-only events (`ReplenishmentChanged`, `SupplierOrdersChanged`) only feed section 2.

## 4. Kafka consumers: redelivery and partitions (must do / should do)

- **Redelivery must be harmless (must do).** In a rolling update the consumer group rebalances, and a message that was
  processed but whose offset was not yet committed is delivered again to the other pod. Delivery messages carry no id,
  and `addAmount` / `receiveDelivery` and `recordStoreSale` are not idempotent: a redelivered delivery is counted twice,
  a redelivered cashpoint sale deducts twice. Fix: a message id in every message (the supplier's delivery id, the
  cashpoint's receipt id) and a `processed_message` table (the "inbox"), written in the same transaction as the stock
  change; a known id is skipped. For the demo's own stubs, the stubs generate the id.
  Already idempotent: the carrier's `shipment-arrivals` (in-transit transfers) - the shipment row is the inbox, only
  an `IN_TRANSIT` shipment can arrive. And `store-occupancy`: its messages are snapshots, stored only if newer than the
  stored one (one upsert, `StoreOccupancyTable.upsertIfNewer`), so a redelivered report changes nothing.
  The automatic tills (`AutoTillsReceiver`, on `OccupancyChanged`) follow from that: the event fires only in the pod
  that consumed the store's report, so only one pod decides per store, as long as the event stays local (the SSE
  fan-out of section 2 must not hand it to the other pod's `AutoTillsReceiver`). The 10 s cooldown after a change is
  in memory: after a rebalance the other pod starts without it - at worst one extra step.
- **Partitions (should do).** With Dev Services every topic has one partition, so only one pod consumes a topic and the
  other one idles (fine for failover, no parallelism). For parallel processing: several partitions per topic, with the
  product name as the message key, so all messages for one product stay in order.
- **Graceful shutdown.** On SIGTERM, Quarkus must stop consuming, finish the message in flight and commit its offset
  before it exits: `quarkus.shutdown.timeout` (e.g. 20 s) below the pod's `terminationGracePeriodSeconds`.

## 5. Database schema: migrations instead of drop-and-create (DONE 2026-10-04)

`quarkus.hibernate-orm.database.generation=drop-and-create` dropped all tables whenever **a pod started**. With two pods,
every restart or rolling update would have wiped the stock of the running pod.

Done (2026-10-04, first triggered by the in-transit transfers: a dev-mode reload restarted the shipment ids while Kafka
kept its arrival messages):
- Flyway migrations: `quarkus-flyway` + `quarkus-flyway-postgresql` in `outbound-postgres`,
  `db/migration/V1__initial_schema.sql`, `migrate-at-start`; `database.generation=validate` in every profile, so an
  entity change without its migration fails at startup (since `plain-sql`, 2026-10-05: no Hibernate any more; the
  flow tests run every query against the migrated schema instead). Flyway takes a database lock, so two pods starting at once
  migrate only once. Tests clean the schema at every app start (`%test.quarkus.flyway.clean-at-start`); dev mode keeps
  the data across live reloads.

Still to keep in mind:
- Rolling updates run old and new versions side by side for a while, so schema changes must be **expand/contract**:
  add a column in one release, start using it in the next, drop the old one in a third. Never rename in place.
- The same holds for Kafka message formats and the SSE / fragment contract between old and new pods: new fields
  optional, readers tolerant.

## 6. Kubernetes and configuration (must do, outside the code)

- **Health probes.** `quarkus-smallrye-health` (not in the build yet) for liveness and readiness. Readiness should
  include Postgres, MongoDB and Kafka, so the Service only routes to a pod that can serve.
- **Rolling update settings.** `maxUnavailable: 0`, `maxSurge: 1`, a `PodDisruptionBudget` with `minAvailable: 1`, and a
  short `preStop` sleep so the Service removes the pod before it stops accepting requests.
- **Long-lived SSE connections.** The Ingress / load balancer read timeout must exceed the 15 s heartbeat of
  `/inventory/events`. No sticky sessions are needed (section 2 makes every pod send every event).
- **External systems as their own deployments.** In dev/test the supplier stubs (`external-outbound-*`) and the
  `CashpointStub` (`external-inbound-kafka`, `@Scheduled` every 100 ms, with its in-memory store simulations) run
  inside the app. In two pods they would run
  twice (twice the cashpoint traffic, two SOAP/REST endpoints, two occupancy reports per store with different numbers,
  and a till change via `PUT /cashpoint-stub/stores/{id}/tills` would reach the simulation of one pod only), and the stubs' in-memory delayed deliveries
  (`LeadTime`) would be lost when a pod stops (for the carrier, `ShipmentCatchUpReceiver` re-sends shipments still in
  transit after 2 min). In Kubernetes they would be separate deployments; the app's REST/SOAP
  client URLs (`%dev` / `%test` point to `localhost`) then come from config.
- **No Dev Services.** Postgres, MongoDB and Kafka come from config (`quarkus.datasource.*`, `quarkus.mongodb.*`,
  `kafka.bootstrap.servers`), secrets from Kubernetes Secrets.

## Suggested order

1. ~~Schema migrations (section 5)~~ - done 2026-10-04.
2. Single-instance period close (section 1).
3. Idempotent Kafka consumers (section 4, redelivery).
4. Cross-pod SSE fan-out via Kafka (section 2).
5. Catch-up of pending requests at the period close (section 3).
6. Health probes, graceful shutdown, stubs as separate deployments (sections 4 and 6).
7. Optional: transactional outbox as a showcase (section 3), more partitions (section 4).

How to test it locally without Kubernetes: start two instances on different ports against the same Postgres, MongoDB
and Kafka (e.g. `java -Dquarkus.http.port=8081 -jar …` twice, with the shared services from `docker compose`), put a
small proxy in front, and run the e2e suite against the proxy while restarting one instance.
