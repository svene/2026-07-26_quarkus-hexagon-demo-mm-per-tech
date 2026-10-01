# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-10-01, on top of commit `a33b45b` plus the uncommitted PLAN.md `shop-product-set-refresh` change (`/shop` poll now morphs the whole products section): `architecture-flow.md` (`GET /shop/inventory-fragment` tree + note), `browser-templating_wip.md` (VM list), `live-updates_wip.md` (current state, step 3). Before that: 2026-09-30, PLAN.md `browser-templating` (Qute → browser-side hono/html), committed as `9146d28`.

**Diff baseline for the next update**: `git diff <commit containing shop-product-set-refresh> HEAD -- . ':(exclude)docs'` - replace with that commit's hash once committed. `9146d28..a33b45b` (*Model → *VM rename, randomize buttons) was only checked for stale names, not fully re-checked. If a doc looks stale for something older, `git diff 982b169 e3d3c87 -- . ':(exclude)docs'` covers the period that was not fully re-checked.

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
