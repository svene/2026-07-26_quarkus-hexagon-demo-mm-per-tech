# AI-facing documentation maintenance

This directory holds the instructions for keeping `docs/architecture/` in sync with the code.
It exists as a separate tree from `docs/architecture/` because its content is written for
whoever performs that maintenance — today, always Claude — rather than for a human trying to
understand the system. If you're looking for the architecture itself (flows, modules, Kafka
topics, diagrams), see `../README.md` and `../architecture/` instead.

---

## When Code Changes

**Small changes** (one endpoint, one class):
- Update only the affected section in the relevant file
- No need to regenerate everything
- Example: if `FruitsHandler` logic changes, update just that handler in `architecture-module-participants.md` and its flow in `architecture-flow.md`

**Medium changes** (new supplier, new module):
- Update all affected files with the new additions
- Add new sequence diagram for new flows
- Still incremental—don't regenerate unaffected sections

**Medium/large changes, one section at a time** (new endpoint, new handler, new Kafka topic, new module):
- Use the `update-architecture-docs` Claude Code skill (`.claude/skills/update-architecture-docs/`) - it makes the surgical single-section edit for you

**Large changes** (major refactoring, new architecture):
- Full regeneration needed
- Use the `recreate-architecture-docs` Claude Code skill (`.claude/skills/recreate-architecture-docs/`) - invoke it with `/recreate-architecture-docs`
- Validate all files afterward

## Update Checklist

When making code changes, check which files need updating:

- [ ] **HTTP endpoint added/removed** → Update `architecture/architecture-flow.md`, create flow diagram
- [ ] **Handler added/renamed** → Update `architecture/architecture-module-participants.md`, `architecture/architecture-flow.md`, flow diagrams
- [ ] **API interface added** → Update `architecture/architecture-module-participants.md`
- [ ] **SPI interface added** → Update `architecture/architecture-module-participants.md`
- [ ] **Service added/renamed** → Update `architecture/architecture-module-participants.md`, flow diagrams
- [ ] **Maven module added** → Add section to `architecture/architecture-module-participants.md`, update summary table
- [ ] **Kafka topic added** → Update `architecture/architecture-flow-kafka-reference.md`, flow diagrams, `application.properties` reference
- [ ] **Supplier added/removed** → Update `architecture/architecture-flow.md`, `architecture/architecture-module-participants.md`, flow diagrams

## Per-file maintenance instructions

- [maintaining-architecture-flow.md](maintaining-architecture-flow.md) - `architecture-flow.md` + `architecture-flow-kafka-reference.md`
- [maintaining-module-participants.md](maintaining-module-participants.md) - `architecture-module-participants.md`
- [maintaining-flows.md](maintaining-flows.md) - `flows/*.puml`

## Validation

After updating:

```bash
# Validate PlantUML syntax
plantuml -checkonly docs/architecture/flows/*.puml

# Check for obvious inconsistencies
grep "ClassName" docs/architecture/*.md | sort | uniq -c  # Should show consistent usage
```

---

## Regeneration and Update Skills

**`recreate-architecture-docs`** (Claude Code skill, `.claude/skills/recreate-architecture-docs/`) - use when major changes need full documentation regeneration:
- Step-by-step instructions for regenerating all files, with a "diff what actually changed first" step that often shrinks the job well below a full rewrite (see the 2026-09-13 feature/cross restructuring for an example: only one file needed a real rewrite)
- File structure templates in the skill's `TEMPLATES.md`
- Validation checklist
- User-invoked only (`/recreate-architecture-docs`) - it will not fire on its own

**`update-architecture-docs`** (Claude Code skill, `.claude/skills/update-architecture-docs/`) - a smaller, autonomous companion for one-section edits after a single endpoint/handler/topic/module change, so the docs don't silently drift between full regenerations.

---

## For Future Sessions

**If you need to regenerate this documentation:** invoke the `recreate-architecture-docs` skill (`/recreate-architecture-docs`) - it contains all the instructions, including a first step that diffs what actually changed so you don't over-rewrite files whose content (class/method/topic names) survived the change intact.

**If you just changed one endpoint, handler, API/SPI, service, topic, or module:** the `update-architecture-docs` skill can fire on its own for this - or invoke it directly - to make the single-section edit without touching the rest of the file.

**Current status and diff baseline for the next update:** see [session-notes.md](session-notes.md).
