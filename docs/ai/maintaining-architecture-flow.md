# Maintaining architecture-flow.md and architecture-flow-kafka-reference.md

Instructions for whoever (today: Claude) keeps `docs/architecture/architecture-flow.md` and
`docs/architecture/architecture-flow-kafka-reference.md` in sync with the code. See
`docs/ai/README.md` for how this fits into the broader doc-maintenance process, and
`update-architecture-docs`/`recreate-architecture-docs` (`.claude/skills/`) for the actual
update workflow.

**When code or Maven modules change**, update both architecture flow files:

1. **architecture-flow.md** (human-readable flows)
   - Update HTTP endpoint descriptions if receiver methods change
   - Add/remove endpoints if handlers are added/removed
   - Update handler/API names if they're refactored
   - Do NOT recreate from scratch—just update the affected sections

2. **architecture-flow-kafka-reference.md** (technical reference)
   - Update Kafka topic configurations if `application.properties` changes
   - Add/remove topic cycles if new suppliers are added
   - Update producer/consumer class names if they're refactored
   - Cross-check with `application.properties` and module source code
   - Do NOT recreate from scratch—just update the affected sections

**Key files to check when maintaining these documents:**
- `app-server/src/main/resources/application.properties` - Kafka topic configuration
- `**/src/main/java/**/feature/<name>/**Receiver.java` and `**/src/main/java/**/cross/**Receiver.java` - HTTP/Kafka entry points
- `core/src/main/java/**/feature/<name>/**Handler.java` and `core/src/main/java/**/cross/**Handler.java` - Business logic
- `**/src/main/java/**/feature/<name>/**Service.java` and `**/src/main/java/**/cross/**Service.java` - Outbound integrations

(Package layout as of 2026-09-13: every module, `core` included, uses `feature.<commodity>` / `cross(.<concern>)` packages - see `docs/architecture/architecture-module-participants.md` for the full mapping. `external-*` modules keep their own `external.*` root, untouched by this scheme.)
