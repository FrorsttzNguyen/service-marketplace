# PR #12 Review — Phase 6 Slice 4: audit_logs jsonb→text fix

- **PR:** [#12 fix(db): align audit_logs old/new_values to text so ddl-auto=validate passes (V10)](https://github.com/FrorsttzNguyen/service-marketplace/pull/12)
- **Branch:** `feat/phase6-audit-jsonb` → `main`
- **Coder:** GLM. **Independent review:** Opus.
- **Date:** 2026-06-17
- **Diff:** +27 / 1 file — `src/main/resources/db/migration/V10__audit_logs_values_to_text.sql`. No entity/app change.

## Verdict

**APPROVE — merge first (unblocks PR #11).** The fix is exactly Option A: a new Flyway migration that alters
`audit_logs.old_values/new_values` from `jsonb` to `text`, matching the `AuditLog` entity. Correct, minimal,
and I independently reproduced that it fixes the runtime boot.

## What I verified (own runs, not asserted)

- ✅ Migration is a **new** `V10` (does not edit V6 — no Flyway checksum break). Entity untouched (already text).
- ✅ Correct SQL: `ALTER COLUMN ... TYPE text USING ...::text` — the `USING` cast is required because
  jsonb→text is not implicit in Postgres. Columns are nullable; NULLs stay NULL. Good WHY comment.
- ✅ **CI green** (PR #12): `./mvnw -B verify` passes → 308 H2 tests still green (H2 builds schema from the
  entity, so it was never affected; V10 only touches the Postgres path).
- ✅ **Reproduced the fix myself** (the point of Slice 4 — CI does NOT test the Postgres boot): clean
  `postgres:16` + `./mvnw spring-boot:run` (common profile, real datasource):
  ```
  Successfully applied 10 migrations to schema "public", now at version v10
  Tomcat started on port 8080
  Started ServiceMarketplaceApplication in 2.843 seconds
  ```
  **No `Schema-validation: wrong column type ... audit_logs` error** — the exact failure I reproduced for
  PR #11 is gone. `ddl-auto=validate` now passes against the Flyway schema. Boot confirmed.

  Note: `/actuator/health` returned 503 in *my isolated repro* only because I ran without Redis
  (`SPRING_CACHE_TYPE=simple`, no redis container) → the Redis health contributor is DOWN. That is an
  artifact of the minimal repro, NOT a V10/app problem. App startup itself succeeded (the validate gate).
  In PR #11's full compose (Redis present), health will be UP — to be confirmed at PR #11 re-verify.

## Findings

None. Clean, surgical, well-documented.

## Merge sequence (recommended)

1. **Merge PR #12** → `main` now has V10.
2. **Rebase PR #11** (`feat/phase6-docker`) onto updated `main` so its image includes V10.
3. **Re-verify PR #11:** `docker compose up --build` → app boots, Flyway applies V1..V10, `validate` passes,
   `/actuator/health` = UP (Redis present this time).
4. **Merge PR #11.** Then Slices 3/5 are unblocked (deploy no longer hits this blocker).

## Note: doc divergence to reconcile at PR #11 merge

GLM committed `docs/pr-11-review.md` + a rewritten `docs/session-notes/session-025.md` onto the
`feat/phase6-docker` branch (not main). `main`'s `session-025.md` (Opus's version) and the branch's differ.
When PR #11 merges, reconcile the session note (keep one coherent handoff).
