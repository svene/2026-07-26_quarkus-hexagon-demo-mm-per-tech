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
(`ArchitectureTest.receivers_construct_domain_values_only_via_parse`) keeps any `*Receiver` from
calling the throwing constructor directly.

## Purchase (HTTP, Kafka, shop)

Implemented all-or-nothing via `PurchaseItem`/`ParsedPurchaseItem` plus the `Purchase`/`ParsedPurchase`
aggregate (see `validation.md` § "Multi-item input"): `ProductApiReceiver.purchase` (`400`),
`CashpointReceiver` (audit log `INVALID: ...`, keep consuming) and `ShopReceiver.checkout` (shop page
re-rendered with `400` and errors). This also closed a bug: a negative purchase quantity used to
*add* stock, since `InventoryService.deductAmount` computes `max(0, amount - delta)`.

## Next steps

Every inbound boundary now sends its *values* through `parse()`, and Kafka's structural failures
are handled. What's left are two smaller gaps plus the business-rule decisions:

- **JSON API: wrongly typed field (unverified).** `{"quantity": "abc"}` to `/api/products/order-*`
  or `/purchase` presumably fails in Jackson before the receiver runs, so the caller would get
  Quarkus's default `400` body rather than the JSON array of violation messages. The admin forms
  had the same gap and now pass the text to `XxxOrder.parse(String, String)` (see `validation.md`).
  For JSON, the likely fix is to make `quantity` a `String` in the request DTOs and use that same
  overload (`PurchaseItem` would need one too). First step: a test to see the actual response.
- **ArchUnit rule scope.** `receivers_construct_domain_values_only_via_parse` only checks `*Receiver`
  classes; a receiver delegating to a helper class that calls the constructor would slip through.
  Nothing does this today. Closing it means checking every non-`core` class in the inbound modules.
- **Business rules (decisions, not validation):** purchasing more than is in stock is silently
  capped at `0` by `InventoryService.deductAmount` — should it be rejected? Orders and purchases have
  no upper quantity limit, while deliveries have `@Max(10_000)`.
