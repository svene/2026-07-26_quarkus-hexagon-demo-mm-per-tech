# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-09-30, on top of commit `f63b54f` - the commit that extracted the checkout form's cart logic from `ShopReceiver.checkout` into a new package-private `ShopCart` (`inbound-http-html`, first unit test in that module: `ShopCartTest`; `assertj-core` added to its pom) updated `architecture-module-participants.md` (table + participant list; also added the previously missing `OrderRequest` interface from `e4f87f1`) and one sentence of `validation.md`'s `ShopReceiver.checkout` bullet. No flow/tree/diagram changed. `f63b54f` (`PurchaseItem.parse(String, String)`) was committed with its docs.

**Diff baseline for the next update**: `git diff f63b54f HEAD -- . ':(exclude)docs'` to see what changed in code since the last content pass (its first commit on top, the `ShopCart` extraction, is already reflected), before deciding which doc section(s) need a surgical edit. If a doc looks stale for something older, `git diff 982b169 e3d3c87 -- . ':(exclude)docs'` covers the period that was not fully re-checked.

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
