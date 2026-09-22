# Validation Rollout — Work In Progress

Tracks how far the "parse, don't validate" pattern (see [`validation.md`](validation.md)) has been
rolled out across the codebase, and what's left. Update this file as boundaries are converted;
`validation.md` itself should stay a description of the pattern, not a status tracker.

## Kafka boundary

Implemented for `feature.fruit` only. Not yet applied to
`feature.meat`/`dairy`/`bakery`/`vegetable`/`beverage`/`nonfood`, which still use their older,
unvalidated or `Optional`-returning `parse()` style (see e.g. `MeatDelivery.parse()`).

## HTTP boundary

Implemented for `POST /api/products/order-fruits` only (`FruitOrder`/`ParsedFruitOrder`).
`AdminReceiver`'s `/admin/order-fruits` HTML form also now constructs a `FruitOrder` (so it can't
reach `FruitsHandler` with invalid data either), but via the throwing constructor rather than its
own `parse()`-based handling. Not yet applied to the other six `order-*` endpoints on
`ProductApiReceiver`/`AdminReceiver`, the `/purchase` endpoint, or `ShopReceiver`, all of which
still accept unvalidated quantities.

## Next steps

- Apply the pattern to the remaining six commodities (Kafka side) and the remaining `order-*`
  endpoints, `/purchase`, and `ShopReceiver` (HTTP side). Replicate the landed shape directly
  (`Xxx implements ParsedXxx`, no wrapper type) — don't reintroduce a generic `Parsed<T>` or a
  private-constructor wrapper class; both were explicitly tried and rejected for this codebase
  (see "Known trade-off" in `validation.md`).
- Give `AdminReceiver`'s HTML forms their own `parse()`-based handling (matching `orderFruits`)
  instead of relying on the throwing constructor, so a violation produces a proper HTML error
  instead of an unhandled exception.
