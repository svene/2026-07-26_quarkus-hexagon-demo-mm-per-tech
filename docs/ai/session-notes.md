# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-09-27, on top of commit `865b81b` - the commit that removed the inbound `*API` interfaces (receivers now inject `*Handler`s directly; new ArchUnit rule `inbound_adapters_do_not_use_spis`) updated every doc alongside the code: `concepts.md`, `README.md`, `architecture-flow.md` (API nodes collapsed into their Handler node), `architecture-module-participants.md`, `validation.md`, `docs/ai/*` and both architecture-docs skills. `865b81b` itself was docs-only, so no code since `e3d3c87` is unaccounted for. `wip_validation.md` and `PLAN.md` were deliberately left untouched (historical/WIP).

**Diff baseline for the next update**: `git diff 865b81b HEAD -- . ':(exclude)docs'` to see what changed in code since the last content pass (its first commit on top, the `*API` removal, is already reflected), before deciding which doc section(s) need a surgical edit. If a doc looks stale for something older, `git diff 982b169 e3d3c87 -- . ':(exclude)docs'` covers the period that was not fully re-checked.

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
