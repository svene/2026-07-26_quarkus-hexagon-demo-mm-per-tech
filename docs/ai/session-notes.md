# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-09-30, on top of commit `de51b95` - the commit that restructured `CashpointReceiver` like the delivery receivers (`receive()` = `logReceived()` + `validated().ifPresent(purchaseHandler::recordStoreSale)`; `"CashpointReceiver: PURCHASE_RECEIVED"` now logged by the receiver for every message, before any check; invalid logged as `"CashpointReceiver: INVALID"`; `PurchaseHandler`'s event renamed `PURCHASE_RECEIVED` → `PURCHASE_PROCESSING`, since only receivers log `…_RECEIVED`) updated the docs alongside the code: `validation.md` (cashpoint bullet), `architecture-flow.md` (two purchase trees), the three purchase `.puml` flows, and one phrase of `wip_validation.md`. `de51b95` itself did the same for the 7 `*DeliveryReceiver`s (`…_INVENTORY_UPDATED` moved into `InventoryHandler`, invalid as `"<Receiver>: INVALID"`) with its docs, so nothing up to it is unaccounted for.

**Diff baseline for the next update**: `git diff de51b95 HEAD -- . ':(exclude)docs'` to see what changed in code since the last content pass (its first commit on top, the `CashpointReceiver` audit-logging restructuring, is already reflected), before deciding which doc section(s) need a surgical edit. If a doc looks stale for something older, `git diff 982b169 e3d3c87 -- . ':(exclude)docs'` covers the period that was not fully re-checked.

**By**: Claude (session analysis)

**Files**:
- `architecture/architecture-flow.md` - Primary flow documentation
- `architecture/architecture-flow-kafka-reference.md` - Technical reference
- `architecture/architecture-module-participants.md` - Module inventory with summary table
- `architecture/flows/` - Sequence diagrams (12 diagrams + README)
- `README.md` (docs root) - Human-facing index
- `ai/README.md` - Claude-facing maintenance index (this directory)
- `ai/maintaining-*.md` - Per-file maintenance instructions
- `../../.claude/skills/recreate-architecture-docs/` - full-regeneration skill (was `recreate-architecture-docs.md`, converted to a skill on 2026-09-13)
- `../../.claude/skills/update-architecture-docs/` - single-section update skill (new on 2026-09-13)

**Total documentation**: ~1500 lines (architecture MD files + PUML files) + AI-facing maintenance docs + 2 skills

**Synchronization status**: ✅ All files synchronized
