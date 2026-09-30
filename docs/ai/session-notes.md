# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-09-30, on top of commit `97af239` plus the uncommitted PLAN.md § 9 change (Qute → browser-side hono/html in `inbound-http-html`): `architecture-flow.md` (intro note + `GET /admin`, `GET /shop` trees, checkout outcome), `architecture-module-participants.md` (`inbound-http-html` section), `architecture-flow-kafka-reference.md` (endpoint table: shells + `/admin/page`, `/shop/page`), `flows/README.md` and `admin-get-dashboard.puml`, `shop-get-catalog.puml`, `shop-checkout.puml` (shell → JSON view → browser render; checkout returns the ShopPage envelope instead of 303).

**Diff baseline for the next update**: `git diff <commit containing § 9> HEAD -- . ':(exclude)docs'` - replace with that commit's hash once committed. If a doc looks stale for something older, `git diff 982b169 e3d3c87 -- . ':(exclude)docs'` covers the period that was not fully re-checked.

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
