---
name: update-architecture-docs
description: Update this project's docs/ architecture documentation with a surgical, single-section edit right after adding, renaming, or removing one HTTP endpoint, Handler, API/SPI interface, outbound Service, Kafka topic, or Maven module. For a major refactoring needing all files rewritten instead, use recreate-architecture-docs.
---

A small code change gets a small doc edit - one section, one file at a time, never a rewrite.

## Steps

1. Identify exactly what changed and where it's documented, using `docs/README.md`'s "Update Checklist" as the map:
   - endpoint change → `docs/architecture-flow.md`, plus a new/updated `docs/flows/*.puml` if the endpoint is new
   - Handler/API/SPI/Service change → `docs/architecture-module-participants.md`, and `docs/architecture-flow.md` if the change is visible in a flow tree
   - Kafka topic change → `docs/architecture-flow-kafka-reference.md`, cross-checked against `application.properties`
   - Maven module change → `docs/architecture-module-participants.md`'s Quick Reference table and its own module section

2. Edit only the affected section(s) in place - the one endpoint's tree, the one module's participant list, the one topic's cycle block. Leave every other line in the file untouched; do not regenerate the file.

3. If it's a genuinely new flow (new endpoint or new topic cycle) with no existing diagram, add one new `.puml` file for it - see `.claude/skills/recreate-architecture-docs/TEMPLATES.md` for the format - and add its entry to `docs/flows/README.md`. Don't skip diagramming a new flow just because this is the "small update" path.

4. Completion criterion: a diff of the touched doc file(s) shows only lines related to this one change - nothing else moved or got rewritten. If honoring that constraint turns out to require touching most of the file anyway, stop and switch to `recreate-architecture-docs` instead.
