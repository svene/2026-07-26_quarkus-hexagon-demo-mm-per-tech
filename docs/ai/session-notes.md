# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-10-01, on top of commit `a33b45b` plus uncommitted PLAN.md changes: `shop-product-set-refresh` (`/shop` morphs the whole products section), plan items renamed from numbers to ids, and `live-updates` (`GET /shop/events` SSE + core `InventoryChangesHandler` replace `/shop` polling): `architecture-flow.md` (`GET /shop/inventory-fragment` and new `GET /shop/events` trees), `architecture-flow-kafka-reference.md` (endpoint table), `architecture-module-participants.md` (`InventoryChangesHandler`, `ShopReceiver`, Handler counts), new `flows/shop-events.puml` + `flows/README.md` entry, `browser-templating_wip.md`, `live-updates_wip.md` (rewritten as the final design). Before that: 2026-09-30, PLAN.md `browser-templating` (Qute → browser-side hono/html), committed as `9146d28`.

**Diff baseline for the next update**: `git diff <commit containing live-updates> HEAD -- . ':(exclude)docs'` - replace with that commit's hash once committed. `9146d28..a33b45b` (*Model → *VM rename, randomize buttons) was only checked for stale names, not fully re-checked. If a doc looks stale for something older, `git diff 982b169 e3d3c87 -- . ':(exclude)docs'` covers the period that was not fully re-checked.

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
