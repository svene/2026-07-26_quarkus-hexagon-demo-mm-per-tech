# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-09-30, on top of commit `32eac5b` - the commit that restructured the 7 Kafka `*DeliveryReceiver`s' audit logging (`receive()` = `logReceived()` + `validated().ifPresent(inventoryHandler::updateXxxAmount)`; tombstones now logged as `…_DELIVERY_RECEIVED` "null payload (tombstone)"; invalid messages logged as `"<Receiver>: INVALID"`; `…_INVENTORY_UPDATED` moved into `InventoryHandler`) updated the docs alongside the code: `validation.md` (Kafka reference example + DLQ/audit paragraph), `architecture-flow.md` (fruit delivery tree), the four delivery `.puml` flows, and one sentence of `wip_validation.md`. Everything up to `32eac5b` was already covered by the 2026-09-27 pass (`32eac5b` is the `*API` removal).

**Diff baseline for the next update**: `git diff 32eac5b HEAD -- . ':(exclude)docs'` to see what changed in code since the last content pass (its first commit on top, the receiver audit-logging restructuring, is already reflected), before deciding which doc section(s) need a surgical edit. If a doc looks stale for something older, `git diff 982b169 e3d3c87 -- . ':(exclude)docs'` covers the period that was not fully re-checked.

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
