# Maintaining docs/architecture/flows/*.puml

Instructions for whoever (today: Claude) keeps the sequence diagrams under
`docs/architecture/flows/` in sync with the code. See `docs/ai/README.md` for how this fits
into the broader doc-maintenance process, and `update-architecture-docs`/`recreate-architecture-docs`
(`.claude/skills/`) for the actual update workflow.

**When flows change:**
1. Update the relevant `.puml` file with new sequence
2. Update `architecture-flow.md` with corresponding text description
3. Keep both files synchronized (they document the same flows)
4. Do NOT regenerate from code—update sequences based on actual code review

**Flows to update when adding new suppliers/receivers:**
- For new product category ordered via REST: add `admin-order-[category].puml` and `api-order-[category].puml`
- For new Kafka topic: add sequence diagram showing producer/consumer cycle
- For new HTTP endpoint: add diagram showing request flow and participants
