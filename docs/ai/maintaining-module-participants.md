# Maintaining architecture-module-participants.md

Instructions for whoever (today: Claude) keeps `docs/architecture/architecture-module-participants.md`
in sync with the code. See `docs/ai/README.md` for how this fits into the broader doc-maintenance
process, and `update-architecture-docs`/`recreate-architecture-docs` (`.claude/skills/`) for the
actual update workflow.

**When new classes are added or refactored**, update both this file and `architecture-flow.md`.

**When adding a new commodity:**

1. Create a new `feature.<name>` package in `core` with `<Name>API`, `<Name>SupplierSPI`, `<Name>Delivery`, `<Name>Handler` as standalone top-level types (no more `APIs.java`/`SPIs.java` containers to extend)
2. Add the corresponding case to `InventoryAPI`/`InventoryHandler` in `cross.inventory` and to `ProductType` in `cross.products`
3. Create a `feature.<name>` package in the appropriate outbound module based on integration type:
   - REST: `outbound-httpclient/feature/<name>/<Name>SupplierService` + REST client
   - SOAP: `outbound-webservice/feature/<name>/<Name>SupplierService` + SOAP client
   - Kafka: `outbound-kafka/feature/<name>/<Name>SupplierService`
4. Create a mock supplier stub (package unchanged, `external.outbound.<tech>`):
   - REST: `external-outbound-rest/<Name>Stub`
   - SOAP: `external-outbound-soap/<Name>Stub`
   - Kafka: `external-outbound-kafka/<Name>Stub`
5. Create a `feature.<name>` package in `inbound-kafka` with `<Name>DeliveryReceiver` if needed
6. No test change needed for the cross-feature-isolation check: `ArchitectureTest` (in `app-server`) delegates to the reusable `TriptychArchitecture` ArchRule (`app-server/src/test/java/.../devsupport/`), whose slices rule covers any `feature.<name>` package automatically once it exists
7. Update `architecture-module-participants.md` and `architecture-flow.md`

**When refactoring class names:**
- Update all references in `architecture-module-participants.md` (module → participants)
- Update `architecture-flow.md`
- Update sequence diagrams in `flows/` directory
- Update any memory/reference files
- Watch for fully-qualified-name collisions across modules when moving a class into `cross` or a shared `feature.<name>` package - two different classes with the same simple name in the same package, in different module jars, will silently shadow each other at runtime with no compile error (see the `AuditLogEntry`/`AuditLogEntryEntity` case in `architecture-module-participants.md`). `maven-enforcer-plugin`'s `banDuplicateClasses` rule (bound to `verify` in `app-server/pom.xml`) catches this at build time.
