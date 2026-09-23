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
exception mapper. `AdminReceiver`'s HTML forms for all seven commodities construct their `XxxOrder`
via the throwing `@Deprecated` constructor (consistent with `orderFruits`, not yet upgraded — see
known gap below).

Not yet applied: the `/purchase` endpoint (`PurchaseRequest`/`PurchaseRequestItem`/`PurchaseItem`)
and `ShopReceiver`'s `/shop/checkout` flow, both still entirely unvalidated — no `ParsedPurchaseItem`
equivalent exists yet.

## Next steps

- Design and apply the pattern to `/purchase` and `ShopReceiver`'s `/shop/checkout` — no
  `ParsedPurchaseItem`-equivalent exists yet; this is a new design, not a replication of the
  landed shape.
- Give `AdminReceiver`'s HTML forms (all seven commodities) their own `parse()`-based handling
  instead of relying on the throwing constructor, so a violation produces a proper HTML error
  instead of an unhandled exception. Explicitly deferred, not an oversight.
