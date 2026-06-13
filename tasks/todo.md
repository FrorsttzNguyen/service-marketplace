# Review Plan — Full Project Audit

## Scope

Review the Service Marketplace repository as a learning/portfolio project:
- Phase 0–4 implementation quality
- Markdown documentation and ADRs
- HTML learning docs
- Tests/build health
- Alignment with project goals: Java/Spring learning vehicle, modular monolith, layered architecture, domain-rich design, PostgreSQL/Redis/Stripe, portfolio readiness

## Tasks

- [x] Map repository structure, docs, phase boundaries, and evidence sources.
- [x] Review Phase 0: foundation, architecture, learning docs, tests.
- [x] Review Phase 1: domain model/value objects/JPA schema/repositories/tests/docs.
- [x] Review Phase 2: API/security/validation/docs/tests.
- [x] Review Phase 3: booking/order business logic/use cases/docs/tests.
- [x] Review Phase 4: payment/refund/webhook implementation/docs/tests.
- [x] Run verification evidence for build/tests.
- [x] Synthesize an honest scorecard, gaps, risks, and next-step documentation/coding plan.

## Verification

- Used read-only phase agents to keep context isolated.
- Cross-checked claims against actual files, docs, and test output.
- Ran `./mvnw test`: 284 tests passed, 0 failures/errors/skips.
- Ran HTML internal link check: 67 HTML files, 600 internal links checked, 27 broken internal links.

## Review Notes

### High-confidence findings

1. Phase 0–4 are broadly implemented and the project is strong as a learning backend portfolio foundation.
2. The test suite currently passes, but it mostly uses H2 with Flyway disabled rather than PostgreSQL/TestContainers.
3. HTML learning docs exist for VI and EN across phases 0–4, but landing/index and language-switch links are stale/broken for Phase 3/4.
4. Critical code review gaps remain around payment/webhook transactional behavior, double-booking database protection, role authorization, and unused ServiceSpecification search.

### Recommended next work

1. Fix correctness/security gaps before increasing phase scores.
2. Repair HTML docs navigation/index/language switcher and update stale evaluation claims.
3. Add PostgreSQL/TestContainers tests for migrations, jsonb mapping, booking overlap constraints, ServiceSpecification, and payment webhook idempotency.
4. Then continue with documentation cleanup or Phase 5 Redis caching.

---

## Current Session — PR #6 Review Handoff Docs

### Plan

- [x] Strengthen project PR review rules so coder fix prompts are saved under `docs/`, not only left in chat.
- [x] Review the unfinished PR #6 follow-up doc for missing verdict, severity, and exact file/line references.
- [x] Finish `docs/pr6-review-fix-prompts-2026-06-13.md` with a clear coder prompt and merge blocker checklist.
- [x] Update the latest session handoff with the completed doc link and next instructions.

### Review

Completed. The PR #6 follow-up now has a short GLM verdict, exact source line references, severity labels, required tests, a copy-paste coder prompt, and a reviewer checklist. `docs/session-notes/session-019.md` links the completed handoff and says PR #6 should not merge yet.

---

## Current Session — Context-Low Final Handoff

### Plan

- [x] Add a 75% context-low handoff rule to global/project rules.
- [x] Record the correction in `tasks/lessons.md`.
- [x] Create the next session note with full context, phase status, and a paste-ready new-session prompt.
- [x] Run lightweight verification for docs/rules edits.

### Review

Completed. `git diff --check` passed. Current tracked/visible working tree has updated `docs/session-notes/session-019.md`, `tasks/todo.md`, and new `docs/pr6-review-fix-prompts-2026-06-13.md`, `docs/session-notes/session-020.md`, `tasks/lessons.md`. Local ignored rule files were also updated: project `CLAUDE.md` and global `/Users/hiennguyen/.claude/CLAUDE.md`.

---

## Current Session — Service Search Pagination/Sorting Amend

### Plan

- [x] Review GLM's pagination/sorting changes against actual files.
- [x] Replace broad pagination `IllegalArgumentException` handling with a dedicated REST exception.
- [x] Implement a real stable `id` tie-breaker for catalog pageable queries.
- [x] Replace the false-positive deterministic sorting test with exact order assertions.
- [x] Run focused service search integration tests.
- [x] Run full test suite.
- [x] Commit the completed amend and session handoff.

### Review

Focused test passed: `./mvnw test -Dtest=ServiceSearchIntegrationTest` → 19 tests, 0 failures/errors/skips, build success. Full suite passed: `./mvnw test` → 323 tests, 0 failures/errors/skips, build success. Committed on `fix/service-search-pagination-sorting`.