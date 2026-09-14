# Architecture Documentation

Complete architecture documentation for the supermarket inventory system. All files are maintained together and derived from the actual codebase.

**This documentation is split by audience**: `docs/architecture/` describes what the system *is* (flows, modules, Kafka topics, diagrams) for anyone — developers, architects, product owners — who wants to understand the system. `docs/ai/` holds the maintenance instructions for keeping these docs in sync with the code, written for whoever (today: Claude) performs that maintenance.

---

## Documentation Files (`docs/architecture/`)

### Primary Flow Documentation

**`architecture/architecture-flow.md`** (human-readable)
- Shows all primary flows originating from external sources (HTTP requests)
- Describes the complete path from receiver → handler → persistence/integrations
- Includes technology stack for each flow (REST, SOAP, Kafka)
- Shows Kafka delivery cycles for orders
- **Audience**: Product owners, architects, new developers
- **Size**: ~280 lines

### Technical Reference

**`architecture/architecture-flow-kafka-reference.md`** (technical deep-dive)
- Documents Kafka topic mappings and configurations
- Lists producer/consumer pairs for each topic
- References application.properties configuration
- Shows data persistence patterns
- Complete endpoint summary table (including indirect flows)
- **Audience**: Backend engineers, integration specialists
- **Size**: ~180 lines

### Module Inventory

**`architecture/architecture-module-participants.md`** (class-to-module mapping)
- Organizes all classes by Maven module
- Quick reference table at the top
- Detailed module sections with responsibilities
- Technology stack per module
- Participant counts and summaries by layer
- **Audience**: All developers (quick lookup)
- **Size**: ~400 lines

### Flow Sequence Diagrams

**`architecture/flows/` directory**
- PlantUML sequence diagrams for each unique flow
- One file per flow (e.g., `admin-order-fruits.puml`)
- Shows detailed message sequences between all participants
- Includes databases, async paths, and error handling
- **Files**: ~12 diagrams covering all flow types
- **Audience**: All developers (visual understanding)

**`architecture/flows/README.md`**
- Index of all sequence diagrams
- Explains common patterns (REST, SOAP, Kafka)
- Provides rendering instructions

---

## Using This Documentation

### For Understanding the System

1. **Start here**: Read `architecture/architecture-flow.md` for an overview of all flows
2. **Then**: Look at specific sequence diagrams in `architecture/flows/` that match your use case
3. **Deep dive**: Read `architecture/architecture-module-participants.md` to understand which classes implement each flow
4. **Technical details**: Consult `architecture/architecture-flow-kafka-reference.md` for Kafka topic mappings and external integrations

### For Adding a New Feature

1. Check `architecture/architecture-module-participants.md` to find the right modules
2. Review related sequence diagrams in `architecture/flows/`
3. Read the relevant sections in `architecture/architecture-flow.md` and `architecture/architecture-flow-kafka-reference.md`
4. **Update the documentation** after making code changes — see `ai/README.md` for how

### For Troubleshooting

1. Use `architecture/architecture-module-participants.md` to find which classes are involved
2. Look up the flow in `architecture/architecture-flow.md` to understand the sequence
3. Check the sequence diagram in `architecture/flows/` for exact message passing
4. Consult `architecture/architecture-flow-kafka-reference.md` for Kafka/persistence details

---

## Key Principles

1. **Code is source of truth** - All documentation derives from actual code, never from diagrams
2. **Kafka cycles are explicit** - Every order endpoint shows its async delivery cycle
3. **Module ownership is clear** - Every class belongs to exactly one module
4. **Flows are complete** - Each flow shows HTTP → handlers → persistence/integrations
5. **Documentation is synchronized** - All files describe the same system from different angles
6. **Incremental updates only** - Don't regenerate files when small changes suffice

---

## File Relationships

```
Source Code (actual implementation)
    ↓
architecture/architecture-flow.md (describes flows)
    ↓
architecture/flows/*.puml (visualizes flows)
    ├─ Shows same flows as architecture-flow.md but in sequence diagram format
    └─ Used for quick visual understanding

Source Code (class inventory)
    ↓
architecture/architecture-module-participants.md (maps classes to modules)
    ├─ Shows which class lives in which module
    └─ Used for finding classes and understanding structure

Source Code (Kafka & external integrations)
    ↓
architecture/architecture-flow-kafka-reference.md (technical details)
    ├─ Documents topic configurations
    ├─ Shows producer/consumer relationships
    └─ Used for integration work and troubleshooting
```

---

## Questions About the Documentation?

**"Which file should I read to understand flow X?"**
- Start with `architecture/architecture-flow.md` for the text description
- Then look at the corresponding `.puml` file in `architecture/flows/` for the sequence diagram

**"Where is class X defined?"**
- Check `architecture/architecture-module-participants.md` - find the module, then the class section

**"How does Kafka topic Y get used?"**
- Check `architecture/architecture-flow-kafka-reference.md` - find the topic section with producer/consumer info

**"I need to add a new feature. Where do I start?"**
- Read `architecture/architecture-flow.md` for similar existing flows
- Check `architecture/architecture-module-participants.md` to understand the module structure
- Create/update the sequence diagram in `architecture/flows/`
- Update the relevant docs after coding — see `ai/README.md`

**"Who keeps this documentation up to date, and how?"**
- See `ai/README.md` — that's the Claude-facing maintenance process, deliberately kept separate from this human-facing index.
