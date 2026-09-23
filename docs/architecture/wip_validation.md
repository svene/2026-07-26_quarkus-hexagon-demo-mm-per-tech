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
commodities, returning the violation messages as a `400` HTML fragment shown below the form. An ArchUnit rule
(`ArchitectureTest.receivers_construct_domain_values_only_via_parse`) keeps any `*Receiver` from
calling the throwing constructor directly.

## Purchase (HTTP, Kafka, shop)

Implemented all-or-nothing via `PurchaseItem`/`ParsedPurchaseItem` plus the `Purchase`/`ParsedPurchase`
aggregate (see `validation.md` § "Multi-item input"): `ProductApiReceiver.purchase` (`400`),
`CashpointReceiver` (audit log `INVALID: ...`, keep consuming) and `ShopReceiver.checkout` (shop page
re-rendered with `400` and errors). This also closed a bug: a negative purchase quantity used to
*add* stock, since `InventoryService.deductAmount` computes `max(0, amount - delta)`.

## Next steps

Every inbound boundary now sends its *values* through `parse()`. What's left are gaps where input
fails before `parse()` is ever reached, plus two business-rule decisions:

- **Kafka: malformed messages are not handled (unverified).** Invalid JSON or a wrongly typed field
  (`"quantity": "abc"`) fails in `DeliveryMessageDeserializer`/`PurchaseMessageDeserializer`, before
  any receiver runs. There is no deserialization failure handler and no
  `fail-on-deserialization-failure` setting, so under SmallRye's defaults this may stop the channel
  rather than log-and-skip. A tombstone (`null` value) would reach the receivers as `null` and throw
  an NPE; so would a cashpoint message with `"items": null` or a `null` entry (the JSON API
  handles both since the "request structure" check, see `validation.md`). Affects all 8 consumers. First step: a
  test that publishes a raw invalid message, to see what actually happens.
- **Admin form: non-numeric quantity.** `@FormParam("quantity") int` fails in JAX-RS before
  `parse()`, so the `400` is Quarkus's default body rather than the `orderErrors` fragment. The
  browser's `type="number"` makes this hard to hit.
- **ArchUnit rule scope.** `receivers_construct_domain_values_only_via_parse` only checks `*Receiver`
  classes; a receiver delegating to a helper class that calls the constructor would slip through.
  Nothing does this today. Closing it means checking every non-`core` class in the inbound modules.
- **Business rules (decisions, not validation):** purchasing more than is in stock is silently
  capped at `0` by `InventoryService.deductAmount` — should it be rejected? Orders and purchases have
  no upper quantity limit, while deliveries have `@Max(10_000)`.
