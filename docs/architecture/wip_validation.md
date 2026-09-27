# Validation Rollout — Work In Progress

Tracks how far the "parse, don't validate" pattern (see [`validation.md`](validation.md)) has been
rolled out across the codebase, and what's left. Update this file as boundaries are converted;
`validation.md` itself should stay a description of the pattern, not a status tracker.

## Kafka boundary

Implemented for all seven commodities: `feature.fruit`/`meat`/`dairy`/`bakery`/`vegetable`/
`beverage`/`nonfood`. Each `XxxDelivery` carries Bean Validation annotations, implements a sibling
`ParsedXxxDelivery`, and each `*DeliveryReceiver` (`inbound-kafka`) `switch`es exhaustively, logging
`Invalid` messages to the audit trail (`"INVALID: <name>, <qty>: <violation messages>"`) instead of
silently dropping them the way the pre-rollout `Optional`-returning `parse()` did.

Messages that fail *before* `parse()` are handled too: undeserializable messages, tombstones,
and cashpoint messages with `"items": null` or `null` entries. They go to the channel's
dead-letter topic `<topic>-dlq`, not to the audit log, and the consumer keeps running. Before the
fix, a probe showed that each of these permanently stopped its channel (see `validation.md`
§ "Reference example: Kafka boundary").

## HTTP boundary

Implemented for all seven `order-*` endpoints on `ProductApiReceiver` (`FruitOrder`, `MeatOrder`,
`DairyOrder`, `BakeryOrder`, `VegetableOrder`, `BeverageOrder`, `NonFoodOrder`, each with a sibling
`ParsedXxxOrder`) — each builds a `400` from the violation messages directly, no `@Valid`, no
exception mapper. `AdminReceiver`'s HTML forms (`inbound-http-html`) do the same for all seven
commodities, returning the violation messages as a `400` HTML fragment shown below the form. A
blank or non-numeric `quantity` on those forms gets the same fragment. An ArchUnit rule
(`ArchitectureTest.inbound_adapters_construct_domain_values_only_via_parse`) keeps any class in an
`inbound-*` module from calling the throwing constructor directly.

## Purchase (HTTP, Kafka, shop)

Implemented all-or-nothing via `PurchaseItem`/`ParsedPurchaseItem` plus the `Purchase`/`ParsedPurchase`
aggregate (see `validation.md` § "Multi-item input"): `ProductApiReceiver.purchase` (`400`),
`CashpointReceiver` (audit log `INVALID: ...`, keep consuming) and `ShopReceiver.checkout` (shop page
re-rendered with `400` and errors). This also closed a bug: a negative purchase quantity used to
*add* stock, since `InventoryService.deductAmount` computed `max(0, amount - delta)`.

## JSON API: malformed input (done 2026-09-27)

Input that Jackson rejected or silently "fixed" before `parse()` ran (`"quantity": "abc"`, `"5"`,
`5.7`, `123` as `productName`, invalid JSON, missing/`null` quantity or productName) now gets our JSON array with a
precise message instead of Quarkus's `{"objectName":…}` body, an empty `400`, the wrong reason, or a
`204`. `quantity` is an `Integer` in the request DTOs and checked as a structure error by each
request record's static `structureErrors(request)` (the receiver then calls the
domain `parse()` as before); a strict, resource-local reader (`StrictJsonReader` via `@CustomDeserialization`)
rejects the coercions; and `ProductApiReceiver`'s `@ServerExceptionMapper` methods delegate to the
shared `JsonInputErrors` (see `validation.md`, "Reference example: HTTP boundary"). Newer Quarkus 3.x wouldn't have fixed this (Jackson 3 only arrives with Quarkus 4), and
`String` DTO fields were rejected because they'd put `quantity` into the API contract as a string.
One finding while implementing: inside a record, Jackson wraps parser errors (out-of-range number,
truncated JSON) in a `JsonMappingException`, so the mapper unwraps one level and takes its path.

## Kafka: transient failures (done 2026-09-27)

`failure-strategy=dead-letter-queue` treated *every* nack as dead, including transient ones: a
valid delivery that arrived during shutdown (`Session/EntityManager is closed`) went to
`fruit-deliveries-dlq` and needed a manual replay, and during a longer database outage every
message would have. Now the receivers carry `@Retry` (3 retries, 1 s apart), and the custom
strategy `dead-letter-or-fail-stop` (`DeadLetterOrFailStop`, `inbound-kafka` `cross.kafka`) sends
only undeserializable records and `UnprocessableMessageException` (tombstone, broken structure;
formerly `IllegalArgumentException`) to the DLQ. Anything else stops the channel without
committing, so the message is reprocessed after a restart. The strategy extends SmallRye's
`KafkaDeadLetterQueue` and delegates to SmallRye's own DLQ and fail-stop handlers, because SmallRye
routes deserialization failures to the handler only if it is an `instanceof KafkaDeadLetterQueue`.
Rejected: `delayed-retry-topic` (can't tell retryable from non-retryable, extra topics, loses
ordering) and retrying forever (SmallRye's throttled commit closes a consumer whose record stays
unprocessed for 60 s anyway). Accepted trade-off: a stopped channel needs a restart.

## Business rules (done 2026-09-27)

- **No overselling online:** `/shop/checkout` and `/api/products/purchase` now go through
  `PurchaseAPI.checkout`, which rejects the whole purchase with `409` if any item lacks stock; row
  locks in product-name order make it safe against concurrent customers (covered by
  `CashpointFlowTest.concurrent_purchases_never_sell_more_than_is_in_stock`, which fails without the
  lock). The cashpoint (`recordStoreSale`) still records every sale, capped at `0`, and logs a
  `STOCK_DISCREPANCY` (an unknown product counts as 0 on record). Both paths share one SPI method,
  `deductAll(quantities, OnShortage)`; the policy is the only difference (user-requested
  simplification). See `validation.md` § "After parsing".
- **Upper limits:** `@Max(2000)` on all 7 `XxxOrder`s, `@Max(50)` on `PurchaseItem` (all three
  purchase channels, cashpoint included — user decision).

## Next steps

None — the validation rollout is complete.
