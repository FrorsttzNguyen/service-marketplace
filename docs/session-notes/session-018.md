# Session 018 — Full Project Review / Phase 0-4 Audit

**Date:** 2026-06-13
**Focus:** Review full Service Marketplace project state across codebase, Markdown docs, HTML learning docs, tests, and portfolio readiness.

---

## What Was Done

### 1. Created and executed review plan

**File:** `tasks/todo.md`

- Created a checkable full-project review plan.
- Used a read-only agent team split by Phase 0, Phase 1, Phase 2, Phase 3, Phase 4, plus verifier context.
- Cross-checked phase/evaluation claims against actual repository files.

### 2. Verified test suite

Command run:

```bash
./mvnw test
```

Result:

```text
Tests run: 284, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 47.114 s
```

### 3. Audited HTML learning docs

Observed HTML doc coverage:

| Phase | VI Docs | EN Docs | Status |
|-------|---------|---------|--------|
| Phase 0 | 5 | 5 | Present |
| Phase 1 | 5 | 5 | Present |
| Phase 2 | 6 | 6 | Present |
| Phase 3 | 6 | 6 | Present, but language-switch links broken |
| Phase 4 | 4 | 4 | Present, but landing/index stale and language-switch links broken |

HTML link validation result:

```text
HTML files: 67
Internal links checked: 600
Broken internal links: 27
```

Main doc/navigation findings:

- `docs/html/index.html` still shows Phase 4 as `0/4` and does not link Phase 4 cards, despite Phase 4 docs existing.
- Several Phase 3/4 language switcher links use `../en/...` or `../vi/...` where `../../en/...` / `../../vi/...` is needed.
- `docs/html/en/roadmap.html` has broken relative links to `vi/...` paths.

### 4. Audited code quality and phase implementation

High-confidence code findings:

1. **Tests pass**, and the codebase is broadly strong for a Java/Spring learning backend.
2. **Test database gap:** `src/test/resources/application-test.yml` uses H2 with `ddl-auto: create-drop` and Flyway disabled, while project rules target TestContainers/PostgreSQL.
3. **TestContainers base classes exist but are unused:** no test class extends `BaseDataJpaTest` or `BaseIntegrationTest`.
4. **Booking double-booking DB protection is incomplete:** unique constraint only covers exact `(service_id, booking_date, start_time)`, not overlapping time ranges.
5. **Payment/webhook transactional handling needs correction:** webhook event log and domain update are documented as atomic, but exceptions are swallowed after event log insert.
6. **Payment/order lookup authorization bug:** `PaymentController.getPaymentByOrder()` accepts the principal but does not authorize ownership before returning payment by order ID.
7. **Role-based access is mostly not enforced with method security:** code comments mention `@PreAuthorize`, but there are no actual `@PreAuthorize` annotations in source.
8. **ServiceSpecification is implemented but unused:** no controller/service endpoint wires `ServiceSearchRequest` into `ServiceSpecification.fromRequest()`.

### 5. Saved visual audit tables for next session

**File:** `docs/project-audit-2026-06-13.md`

Saved the 11 evaluation tables from the final audit response:

1. Overall conclusion table
2. Verification evidence table
3. Revised phase scorecard
4. Detailed phase rubric
5. Critical findings table
6. HTML docs audit table
7. Portfolio readiness table
8. “5 phase implement ổn không?” table
9. GLM coder task order table
10. Suggested operating model table
11. Files changed table

---

## Current Project State

- **Branch:** `main`
- **Working tree:** modified by this review session
- **Tests:** 284 passing (`./mvnw test`)
- **Team context:** temporary `phase-review` team was created and cleaned up

### Files changed this session

1. `tasks/todo.md` — created/updated review plan and review notes.
2. `docs/project-audit-2026-06-13.md` — saved 11 visual audit/evaluation tables for next-session reference.
3. `docs/session-notes/session-018.md` — this handoff note.

No production code was changed.

---

## Learning Docs Status

| Phase | Learning docs status | Main issue |
|-------|----------------------|------------|
| Phase 0 | Complete VI/EN docs | Good overall |
| Phase 1 | Complete VI/EN docs | Good overall |
| Phase 2 | Complete VI/EN docs | Some stale claims should be rechecked |
| Phase 3 | Complete VI/EN docs | Broken language-switch links |
| Phase 4 | Complete VI/EN docs | Broken language-switch links; landing/index stale |
| Phase 5 | Not started | Expected next phase: Redis caching |
| Phase 6 | Not started | Frontend directory exists but is empty |
| Phase 7 | Not started | Architecture polish/CI/coverage/README pending |

---

## Next Session Instructions

Priority order:

1. **Fix code correctness/security gaps first**
   - Make webhook idempotency atomic: do not mark event processed if domain update fails.
   - Fix `PaymentController.getPaymentByOrder()` authorization.
   - Fix vendor service management to resolve vendor by `userId`, not assume `userId == vendorId`.
   - Wire `ServiceSpecification` into a real search endpoint or document it as not implemented.

2. **Upgrade test realism**
   - Add real TestContainers/PostgreSQL tests using existing base classes.
   - Add tests for Flyway migrations, jsonb mapping, booking overlap races, ServiceSpecification filters, payment webhook idempotency, and payment-order authorization.

3. **Fix HTML docs/navigation**
   - Update `docs/html/index.html` for Phase 4.
   - Repair Phase 3/4 language switcher paths.
   - Repair `docs/html/en/roadmap.html` relative links.
   - Remove `.backup` and `.DS_Store` files if not needed.

4. **Then decide next direction**
   - Documentation cleanup/polish if Hien wants the project to look portfolio-ready now.
   - Or Phase 5 Redis caching implementation after code gaps are addressed.

---

## Blocking Issues / Decisions Needed

1. Decide whether to pause Phase 5 and first fix P0/P1 correctness issues from this audit.
2. Decide whether HTML learning docs should stay local-only or become part of portfolio-visible docs.
3. Decide whether GLM should handle code fixes while the main review model continues as architecture/review brain.

---

## Summary

The project is strong and tests pass, but the current historical phase scores are slightly too optimistic because several important implementation and documentation gaps remain. Best next move: fix the correctness/security gaps and docs navigation before starting Phase 5.
