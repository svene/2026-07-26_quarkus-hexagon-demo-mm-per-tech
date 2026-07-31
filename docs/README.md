# Architecture Documentation

Complete architecture documentation for the supermarket inventory system. All files are maintained together and derived from the actual codebase.

**All documentation files in this directory must be kept synchronized. When code changes, update the relevant files using the regeneration guide.**

---

## Documentation Files

### Primary Flow Documentation

**`architecture-flow.md`** (human-readable)
- Shows all primary flows originating from external sources (HTTP requests)
- Describes the complete path from receiver → handler → persistence/integrations
- Includes technology stack for each flow (REST, SOAP, Kafka)
- Shows Kafka delivery cycles for orders
- **Audience**: Product owners, architects, new developers
- **Size**: ~280 lines
- **Update frequency**: When HTTP endpoints change, handlers added/removed, or flow logic changes

### Technical Reference

**`architecture-flow-kafka-reference.md`** (technical deep-dive)
- Documents Kafka topic mappings and configurations
- Lists producer/consumer pairs for each topic
- References application.properties configuration
- Shows data persistence patterns
- Complete endpoint summary table (including indirect flows)
- **Audience**: Backend engineers, integration specialists
- **Size**: ~180 lines
- **Update frequency**: When Kafka topics change, suppliers added/removed, or external integrations change

### Module Inventory

**`architecture-module-participants.md`** (class-to-module mapping)
- Organizes all classes by Maven module
- Quick reference table at the top
- Detailed module sections with responsibilities
- Technology stack per module
- Participant counts and summaries by layer
- **Audience**: All developers (quick lookup)
- **Size**: ~400 lines
- **Update frequency**: When classes refactored, renamed, or new modules created

### Flow Sequence Diagrams

**`flows/` directory**
- PlantUML sequence diagrams for each unique flow
- One file per flow (e.g., `admin-order-fruits.puml`)
- Shows detailed message sequences between all participants
- Includes databases, async paths, and error handling
- **Files**: ~12 diagrams covering all flow types
- **Audience**: All developers (visual understanding)
- **Update frequency**: When flows change significantly

**`flows/README.md`**
- Index of all sequence diagrams
- Explains common patterns (REST, SOAP, Kafka)
- Provides rendering instructions
- Maintenance guidance for adding new flows

---

## Using This Documentation

### For Understanding the System

1. **Start here**: Read `architecture-flow.md` for an overview of all flows
2. **Then**: Look at specific sequence diagrams in `flows/` that match your use case
3. **Deep dive**: Read `architecture-module-participants.md` to understand which classes implement each flow
4. **Technical details**: Consult `architecture-flow-kafka-reference.md` for Kafka topic mappings and external integrations

### For Adding a New Feature

1. Check `architecture-module-participants.md` to find the right modules
2. Review related sequence diagrams in `flows/`
3. Read the relevant sections in `architecture-flow.md` and `architecture-flow-kafka-reference.md`
4. **Update all documentation** after making code changes (see guide below)

### For Troubleshooting

1. Use `architecture-module-participants.md` to find which classes are involved
2. Look up the flow in `architecture-flow.md` to understand the sequence
3. Check the sequence diagram in `flows/` for exact message passing
4. Consult `architecture-flow-kafka-reference.md` for Kafka/persistence details

---

## Maintaining This Documentation

### When Code Changes

**Small changes** (one endpoint, one class):
- Update only the affected section in the relevant file
- No need to regenerate everything
- Example: if `FruitsHandler` logic changes, update just that handler in `architecture-module-participants.md` and its flow in `architecture-flow.md`

**Medium changes** (new supplier, new module):
- Update all four files with the new additions
- Add new sequence diagram for new flows
- Still incremental—don't regenerate unaffected sections

**Large changes** (major refactoring, new architecture):
- Full regeneration needed
- Follow `recreate-architecture-docs.md` guide
- Validate all files afterward

### Update Checklist

When making code changes, check which files need updating:

- [ ] **HTTP endpoint added/removed** → Update `architecture-flow.md`, create flow diagram
- [ ] **Handler added/renamed** → Update `architecture-module-participants.md`, `architecture-flow.md`, flow diagrams
- [ ] **API interface added** → Update `architecture-module-participants.md`
- [ ] **SPI interface added** → Update `architecture-module-participants.md`
- [ ] **Service added/renamed** → Update `architecture-module-participants.md`, flow diagrams
- [ ] **Maven module added** → Add section to `architecture-module-participants.md`, update summary table
- [ ] **Kafka topic added** → Update `architecture-flow-kafka-reference.md`, flow diagrams, `application.properties` reference
- [ ] **Supplier added/removed** → Update `architecture-flow.md`, `architecture-module-participants.md`, flow diagrams

### Validation

After updating:

```bash
# Validate PlantUML syntax
plantuml -checkonly docs/flows/*.puml

# Check for obvious inconsistencies
grep "ClassName" docs/*.md | sort | uniq -c  # Should show consistent usage
```

---

## Regeneration Guide

**Read this first** when major changes need full documentation regeneration:

**`recreate-architecture-docs.md`**
- Step-by-step instructions for regenerating all files
- Code scanning procedures to extract accurate information
- File structure templates
- Validation checklist
- Debugging tips
- **Time estimate**: 2-3 hours for full regeneration

---

## Key Principles

1. **Code is source of truth** - All documentation derives from actual code, never from diagrams
2. **Kafka cycles are explicit** - Every order endpoint shows its async delivery cycle
3. **Module ownership is clear** - Every class belongs to exactly one module
4. **Flows are complete** - Each flow shows HTTP → handlers → persistence/integrations
5. **Documentation is synchronized** - All four files describe the same system from different angles
6. **Incremental updates only** - Don't regenerate files when small changes suffice

---

## File Relationships

```
Source Code (actual implementation)
    ↓
architecture-flow.md (describes flows)
    ↓
flows/*.puml (visualizes flows)
    ├─ Shows same flows as architecture-flow.md but in sequence diagram format
    └─ Used for quick visual understanding

Source Code (class inventory)
    ↓
architecture-module-participants.md (maps classes to modules)
    ├─ Shows which class lives in which module
    └─ Used for finding classes and understanding structure

Source Code (Kafka & external integrations)
    ↓
architecture-flow-kafka-reference.md (technical details)
    ├─ Documents topic configurations
    ├─ Shows producer/consumer relationships
    └─ Used for integration work and troubleshooting
```

---

## Questions About the Documentation?

**"Which file should I read to understand flow X?"**
- Start with `architecture-flow.md` for the text description
- Then look at the corresponding `.puml` file in `flows/` for the sequence diagram

**"Where is class X defined?"**
- Check `architecture-module-participants.md` - find the module, then the class section

**"How does Kafka topic Y get used?"**
- Check `architecture-flow-kafka-reference.md` - find the topic section with producer/consumer info

**"I need to add a new feature. Where do I start?"**
- Read `architecture-flow.md` for similar existing flows
- Check `architecture-module-participants.md` to understand the module structure
- Create/update the sequence diagram in `flows/`
- Update the relevant `.md` files after coding

---

## Documentation Maintenance Notes

**Last Updated**: 2026-07-31

**By**: Claude (session analysis)

**Files**:
- `architecture-flow.md` - Primary flow documentation
- `architecture-flow-kafka-reference.md` - Technical reference
- `architecture-module-participants.md` - Module inventory with summary table
- `flows/` - Sequence diagrams (12 diagrams + README)
- `recreate-architecture-docs.md` - Regeneration guide
- `README.md` - This file

**Total documentation**: ~1500 lines (4 MD files + 12 PUML files)

**Synchronization status**: ✅ All files synchronized

---

## For Future Sessions

**If you need to regenerate this documentation:**

1. Read `recreate-architecture-docs.md` - it contains all the instructions
2. Follow the step-by-step process for extracting data from code
3. Regenerate each file in order (flows → participants → flow reference → architecture flow)
4. Run validation checks
5. Update memory system if process changed

**You don't need to ask Claude to explain the system again** - just point Claude to `recreate-architecture-docs.md` and it will know exactly what to do.
