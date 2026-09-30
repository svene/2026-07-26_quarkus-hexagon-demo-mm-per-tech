# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-09-30, on top of commit `0453b42` - the commit that made the HTTP receivers log receipts (`AdminReceiver`/`ProductApiReceiver` `order…` POSTs log `"<Receiver>: <X>_ORDER_RECEIVED"`, `ProductApiReceiver.purchase` and `ShopReceiver.checkout` log `PURCHASE_RECEIVED`, GETs don't log; the 7 order handlers' `…_ORDER_RECEIVED` renamed `…_ORDER_PROCESSING`; new package-private `OrderRequest` interface in `inbound-http-jsonapi`) updated the docs alongside the code: `architecture-flow.md` (10 POST trees), the six POST `.puml` flows, and a paragraph under `validation.md`'s boundary table. `de51b95` (delivery receivers) and `0453b42` (`CashpointReceiver`, `PURCHASE_PROCESSING`) did the Kafka side with their docs, so nothing up to `0453b42` is unaccounted for.

**Diff baseline for the next update**: `git diff 0453b42 HEAD -- . ':(exclude)docs'` to see what changed in code since the last content pass (its first commit on top, the HTTP receivers' receipt logging, is already reflected), before deciding which doc section(s) need a surgical edit. If a doc looks stale for something older, `git diff 982b169 e3d3c87 -- . ':(exclude)docs'` covers the period that was not fully re-checked.

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
