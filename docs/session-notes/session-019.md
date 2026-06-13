# Session 019 — Review GLM Fix Scope

**Date:** 2026-06-13
**Focus:** Review the Phase 0–4 audit findings and turn them into concrete GLM coder instructions.

---

## What Was Done

- Read `docs/project-audit-2026-06-13.md`, `docs/session-notes/session-018.md`, and `tasks/todo.md`.
- Cross-checked the highest-priority audit findings against source code instead of relying only on the previous summary.
- Reviewed payment/webhook transaction flow, payment-by-order authorization, vendor user/profile lookup, service search specification wiring, booking overlap protection, TestContainers usage, security role enforcement, refund enum/schema mismatch, HTML doc index, and README doc links.
- Created detailed GLM handoff plan: `docs/glm-fix-plan-2026-06-13.md`.
- No production code was changed.

---

## Current Project State

- **Branch:** `main`
- **Working tree at session start:** already had untracked audit/session/todo files from session 018.
- **Files changed this session:**
  - `docs/glm-fix-plan-2026-06-13.md` — detailed GLM fix plan, PR plan, task prompts, tests, acceptance criteria.
  - `docs/session-notes/session-019.md` — this updated handoff note.
- **Tests:** not re-run in this session; no production code changed. Previous session recorded `./mvnw test` as 284 passing.
- **Production code:** unchanged.

---

## Learning Docs Status

| Phase | Learning docs status | Main issue |
|-------|----------------------|------------|
| Phase 0 | Present VI/EN | No urgent issue found this session |
| Phase 1 | Present VI/EN | No urgent issue found this session |
| Phase 2 | Present VI/EN | Some implementation/security claims should be rechecked after fixes |
| Phase 3 | Present VI/EN | Language-switch links broken |
| Phase 4 | Present VI/EN | Landing/index stale; language-switch links broken |
| Phase 5 | Not started | Redis caching pending |
| Phase 6 | Not started | Frontend pending |
| Phase 7 | Not started | README/architecture/CI polish pending |

---

## Next Session Instructions

Use `docs/glm-fix-plan-2026-06-13.md` as the main handoff file for GLM.

Recommended GLM order remains correctness first:

1. Fix Stripe webhook idempotency/transaction semantics.
2. Fix `@Transactional protected` self-invocation in Payment/Refund flows.
3. Fix `PaymentController.getPaymentByOrder()` authorization.
4. Fix vendor service management to resolve `Vendor` by authenticated `userId`, not assume `userId == vendorId`.
5. Enforce role security consistently.
6. Wire `ServiceSpecification` into public catalog search and add tests.
7. Add PostgreSQL/TestContainers tests for migrations/jsonb/spec/payment/booking behavior.
8. Fix booking overlap DB protection with a PostgreSQL-backed strategy.
9. Fix refund status DB check mismatch if `PROCESSING` can be persisted.
10. Repair HTML docs links/index and update README/architecture docs.

Recommended prompt to GLM:

```text
Read docs/glm-fix-plan-2026-06-13.md fully before coding. Create branch fix/phase4-audit-correctness. Fix tasks in priority order, one focused change at a time. Add/update tests for every behavior change. Run ./mvnw test after meaningful changes. Do not do speculative refactors. Report files changed, tests run, and any deferred items.
```

---

## Blocking Issues / Decisions Needed

- Decide whether GLM should fix tasks one-by-one with review after each diff, or produce one larger P0/P1 branch. Recommendation: one focused diff per priority item.
- Decide whether booking overlap DB protection should use a PostgreSQL exclusion constraint/range strategy now, or a transactional lock strategy first for simpler learning. Recommendation in the GLM plan: PostgreSQL exclusion constraint plus TestContainers test.
- Decide whether role-based security should be implemented with method security (`@EnableMethodSecurity` + `@PreAuthorize`) or URL-level request matchers in `SecurityConfig`. Recommendation in the GLM plan: URL-level rules first for simplicity.

---

## Summary

The detailed GLM handoff now exists at `docs/glm-fix-plan-2026-06-13.md`. It includes exact tasks, evidence, fix directions, acceptance criteria, test requirements, PR/commit plan, and copy-paste prompts for GLM.
