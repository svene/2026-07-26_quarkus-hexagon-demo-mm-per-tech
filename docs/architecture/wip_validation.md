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

## HTTP boundary

Implemented for all seven `order-*` endpoints on `ProductApiReceiver` (`FruitOrder`, `MeatOrder`,
`DairyOrder`, `BakeryOrder`, `VegetableOrder`, `BeverageOrder`, `NonFoodOrder`, each with a sibling
`ParsedXxxOrder`) — each builds a `400` from the violation messages directly, no `@Valid`, no
exception mapper. `AdminReceiver`'s HTML forms (`inbound-http-html`) do the same for all seven
commodities, returning the violation messages as a `text/plain` `400`. An ArchUnit rule
(`ArchitectureTest.receivers_construct_domain_values_only_via_parse`) keeps any `*Receiver` from
calling the throwing constructor directly.

## Purchase (HTTP, Kafka, shop)

Implemented all-or-nothing via `PurchaseItem`/`ParsedPurchaseItem` plus the `Purchase`/`ParsedPurchase`
aggregate (see `validation.md` § "Multi-item input"): `ProductApiReceiver.purchase` (`400`),
`CashpointReceiver` (audit log `INVALID: ...`, keep consuming) and `ShopReceiver.checkout` (shop page
re-rendered with `400` and errors). This also closed a bug: a negative purchase quantity used to
*add* stock, since `InventoryService.deductAmount` computes `max(0, amount - delta)`.

## Next steps

- Show `AdminReceiver`'s `400` violation messages in the admin page — the forms use
  `hx-swap="none"`, so under htmx the error response is currently not rendered anywhere.
  Deferred until after the htmx 4 upgrade.
