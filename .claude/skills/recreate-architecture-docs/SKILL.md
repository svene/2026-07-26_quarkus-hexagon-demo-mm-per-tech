---
name: recreate-architecture-docs
description: Regenerate this project's docs/architecture/ architecture documentation (architecture-flow.md, architecture-flow-kafka-reference.md, architecture-module-participants.md, docs/architecture/flows/*.puml) from the current codebase after a major change - new module, renamed core layer, package restructuring. Use when the user asks to update, regenerate, refresh, or fix the architecture docs.
disable-model-invocation: true
---

`docs/README.md` is the index of what these four files are for and who reads them (human-facing); `docs/ai/README.md` is the maintenance-process index (Claude-facing) - skim both first if this is your first time touching them.

## Steps

1. **Diff what actually changed**, per file, against the current code: class names, method names, HTTP paths, Kafka topic names, package paths, module list. For each of the four files, decide "unaffected", "targeted edit", or "full rewrite" before touching anything.
   - Completion criterion: you can state, for each file, which of the three it needs and why - "targeted, only the package-path lines in the maintenance-notes section" is a valid answer; "not sure, I'll just rewrite it" is not.
   - Names (class/method/path/topic) surviving a refactor is common and shrinks the job a lot - a restructuring that only moves packages needs far less than one that also renames things. Don't assume "major refactor" always means "rewrite all four files"; check first.

2. **For a full rewrite**: extract every fact straight from the current code - grep/read the actual Receivers, Handlers, APIs, SPIs, Services, stubs, and `application.properties` - never from an existing diagram, a prior version of the doc, or memory of an earlier session. See `TEMPLATES.md` for the section structure each file expects and what to read where.

3. **For a targeted edit**: grep the file for the exact stale strings (old package paths, renamed classes) and fix them in place. Leave every unaffected section untouched.

4. **Validate** before calling it done:
   - Every HTTP endpoint in the code has a matching flow tree in `docs/architecture/architecture-flow.md`.
   - Every Kafka topic in `application.properties` has a producer/consumer pair in `docs/architecture/architecture-flow-kafka-reference.md`, and the two files' topic cycles agree with each other.
   - Every module in `docs/architecture/architecture-module-participants.md` lists package paths that actually exist in the source tree (grep for them - don't trust the previous doc).
   - `plantuml -checkonly docs/architecture/flows/*.puml` passes. If it reports "contains errors" with no other detail, don't assume the diagram syntax is broken - check whether the installed `plantuml` is simply too old for a directive the file uses (e.g. `!theme`) by testing a scratch copy with that line removed first.

5. **Close the loop**: update `docs/ai/session-notes.md`'s "Last Updated" / "Synchronization status" fields, and update this project's Claude memory (`architecture-flow-maintenance` and/or a new entry) if what changed is worth remembering for next time - especially if this run's actual scope surprised you relative to what step 1 predicted. If content moved between `docs/architecture/` (human-facing) and `docs/ai/` (Claude-facing maintenance instructions) rather than just changing, keep that split intact - don't collapse the two back into one file.
