# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-09-30, on top of commit `e4f87f1` - the commit that refactored `ShopReceiver.checkout` (`cartRows()`/`CartRow`, early return for an empty cart, `errorsOf()`, `redirectToShop()`; the hand-written `try/catch` number check replaced by the new `PurchaseItem.parse(String, String)` text overload in `core`) updated `validation.md`'s `ShopReceiver.checkout` bullet alongside the code. No flow/tree/diagram changed (same calls, same audit events). `e4f87f1` itself (HTTP receivers log `…_RECEIVED`, order handlers `…_PROCESSING`) was committed with its docs, so nothing up to it is unaccounted for.

**Diff baseline for the next update**: `git diff e4f87f1 HEAD -- . ':(exclude)docs'` to see what changed in code since the last content pass (its first commit on top, the `ShopReceiver.checkout` refactoring, is already reflected), before deciding which doc section(s) need a surgical edit. If a doc looks stale for something older, `git diff 982b169 e3d3c87 -- . ':(exclude)docs'` covers the period that was not fully re-checked.

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
