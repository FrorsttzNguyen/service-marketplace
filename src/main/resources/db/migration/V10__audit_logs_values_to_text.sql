-- =============================================================================
-- V10: Align audit_logs.old_values / new_values to the AuditLog entity mapping
-- =============================================================================
-- WHY this migration exists (Phase 6 Slice 4):
--   V6 created these two columns as JSONB. The entity AuditLog.java maps them as
--   @Column(columnDefinition = "text") private String, and the app never queries the
--   JSON content (the values are read/written as an opaque JSON string). So the jsonb
--   type buys nothing here and actively breaks startup: common application.yml runs
--   spring.jpa.hibernate.ddl-auto=validate, and Hibernate refuses to map a Java String
--   (expected Types#VARCHAR / text) onto a jsonb (Types#OTHER) column:
--     "Schema-validation: wrong column type encountered in column [new_values] in table
--      [audit_logs]; found [jsonb (Types#OTHER)], but expecting [text (Types#VARCHAR)]"
--   This same mismatch also blocks Testcontainers integration tests (BaseIntegrationTest).
--
--   Fix = Option A from docs/phase6-production-readiness-spec.md (Slice 4): change the
--   DB column type to text so it matches the entity. We do NOT edit V6 in place (Flyway
--   checksums would reject it) and we do NOT change AuditLog.java (it is already text).
--
--   `USING ... ::text` is required because jsonb -> text is not an implicit cast in
--   Postgres; the USING clause renders each JSON value to its text representation.
--   The columns are nullable, so rows with NULL stay NULL (no NULL-coalescing needed).
-- =============================================================================

-- Align audit_logs to AuditLog entity (String, columnDefinition="text"). The app never queries the
-- JSON content (mapped as opaque String), so jsonb buys nothing and breaks ddl-auto=validate at boot.
ALTER TABLE audit_logs ALTER COLUMN old_values TYPE text USING old_values::text;
ALTER TABLE audit_logs ALTER COLUMN new_values TYPE text USING new_values::text;
