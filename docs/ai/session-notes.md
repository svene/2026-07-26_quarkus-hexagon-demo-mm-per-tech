# Documentation Maintenance Notes

Session-to-session bookkeeping for the `docs/architecture/` doc set. See [README.md](README.md)
for the actual maintenance process.

**Last Updated**: 2026-09-14, commit `cafd818` + uncommitted restructuring (split `docs/` into `docs/architecture/` (human-facing) and `docs/ai/` (Claude-facing maintenance instructions); moved `architecture-flow.md`, `architecture-flow-kafka-reference.md`, `architecture-module-participants.md`, and `flows/` under `architecture/`; extracted each file's maintenance/update-checklist content into `docs/ai/`)

**Diff baseline for the next update**: `git diff cafd818 HEAD -- . ':(exclude)docs'` to see what changed in code since the last content pass (the restructuring above moved doc files but changed no doc content, so this baseline is still valid), before deciding which doc section(s) need a surgical edit.

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
