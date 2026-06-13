# Session 019 — Review GLM Fix Scope and PR #6

**Date:** 2026-06-13
**Focus:** Review the Phase 0–4 audit findings, turn them into concrete GLM coder instructions, then review GLM PR #6.

---

## What Was Done

- Read `docs/project-audit-2026-06-13.md`, `docs/session-notes/session-018.md`, and `tasks/todo.md`.
- Cross-checked the highest-priority audit findings against source code instead of relying only on the previous summary.
- Reviewed payment/webhook transaction flow, payment-by-order authorization, vendor user/profile lookup, service search specification wiring, booking overlap protection, TestContainers usage, security role enforcement, refund enum/schema mismatch, HTML doc index, and README doc links.
- Created detailed GLM handoff plan: `docs/glm-fix-plan-2026-06-13.md`.
- Reviewed GLM PR #6 / branch `fix/phase4-audit-correctness` after GLM completed Tasks 1–5.
- Ran full test suite on PR branch: `./mvnw test` → 284 tests passing, build success.
- Completed PR #6 review follow-up doc: `docs/pr6-review-fix-prompts-2026-06-13.md`.
  - Includes exact file/line references, severity, why each issue matters, required tests, review checklist, and a copy-paste GLM prompt.
  - Short verdict for GLM: `Amend PR #6 on the same branch. Do not merge yet.`
- Updated project PR review rules in `CLAUDE.md` so future PR reviews must save coder fix prompts under `docs/`, not only in chat.
- No production code was changed by this review model; only rules/review/session/task docs were updated locally.

---

## Current Project State

- **Current branch during review:** `fix/phase4-audit-correctness`
- **PR:** #6 — `fix: Address audit correctness and security gaps`
- **PR URL:** https://github.com/FrorsttzNguyen/service-marketplace/pull/6
- **Tests:** `./mvnw test` passed — `Tests run: 284, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- **Files changed by this review session:**
  - `docs/glm-fix-plan-2026-06-13.md` — detailed GLM fix plan, PR plan, task prompts, tests, acceptance criteria.
  - `docs/pr6-review-fix-prompts-2026-06-13.md` — PR #6 amend prompt, exact findings, severity, required tests, and reviewer checklist.
  - `docs/session-notes/session-019.md` — this updated handoff note.
  - `CLAUDE.md` — strengthened PR review handoff rule to require persistent `docs/` fix-prompt files.
  - `tasks/todo.md` — current-session doc completion tracking.

---

## PR #6 Review Findings

High-confidence issues found in PR #6:

1. **Webhook duplicate handling still risky on PostgreSQL**
   - `StripeWebhookHandler.processEvent()` catches `DataIntegrityViolationException` from `saveAndFlush()` inside the same transaction.
   - On PostgreSQL, a duplicate-key error aborts the transaction, so returning from the catch can still fail commit and return 500 instead of 200 for duplicate webhook delivery.

2. **Webhook malformed payload/header errors can return 500**
   - `StripeWebhookController` maps only `StripeApiException` to 400.
   - Other malformed request failures can fall into the generic 500 path, causing Stripe to retry permanently bad requests.

3. **Supported webhook events with missing local Payment now retry forever**
   - `ResourceNotFoundException` from `PaymentService.handlePaymentSucceeded/Failed()` now escapes and rolls back the event log.
   - This may be correct for transient local failures, but cross-environment/stale PaymentIntent events will become permanent 500 retries unless explicitly handled.

4. **Payment creation still has race/stale-order risk**
   - `PaymentService.createPayment()` checks `existsByOrderId()` before Stripe call and passes a pre-Stripe `Order` object into `PaymentTransactionService`.
   - `PaymentTransactionService` does not reload/lock/recheck order state or duplicate payment inside the transaction.
   - `payments.order_id` has only an index, not a unique constraint.

5. **Refund flow still uses lazy JPA graph outside transaction**
   - `RefundService.createRefund()` is non-transactional and accesses `payment.getOrder().getCustomer()` and `payment.getRefunds()` after `paymentRepository.findById()`.
   - With `spring.jpa.open-in-view=false` and lazy relationships, this can throw `LazyInitializationException` before Stripe call.

6. **Full refund order update may not persist**
   - `RefundTransactionService` calls `payment.getOrder().refund()` then `paymentRepository.save(payment)`.
   - `Payment.order` is `@ManyToOne(fetch = LAZY)` with no cascade; the comment "Cascades to order" is incorrect.

7. **Payment-by-order auth fix leaks existence and returns wrong status**
   - `getPaymentByOrderId()` does `findByOrderId()` first, then throws `BusinessRuleViolationException` if not owner.
   - Result: 404 when no payment exists, 422 when someone else's payment exists. Controller docs say 403.

8. **Refund amount validation is still race-prone**
   - Existing refunded total is calculated before Stripe call and outside the transactional insert.
   - Concurrent partial refunds can both validate against stale refunded totals.

9. **Vendor role guard is incomplete for vendor booking endpoint**
   - PR protects `/api/vendor/**`, but vendor booking route is `GET /api/bookings/vendor`.
   - CUSTOMER token can reach controller and receive service-layer 422 instead of security-layer 403.

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

Priority order:

1. Ask GLM/coder to amend PR #6 before merge using `docs/pr6-review-fix-prompts-2026-06-13.md`.
2. Send this short verdict first:

```text
Amend PR #6 on the same branch. Do not merge yet.
```

3. Then paste the full coder prompt from `docs/pr6-review-fix-prompts-2026-06-13.md` Section 4.
4. After GLM amends the PR, review the actual diff again and run tests. Start the review from `docs/pr6-review-fix-prompts-2026-06-13.md` Section 5 checklist.
5. Do **not** merge PR #6 until the blocker checklist passes or any deferral is explicit, justified, and documented in the PR.

---

## Blocking Issues / Decisions Needed

- Decide desired Stripe behavior for valid supported events whose local Payment is missing:
  - Retry/alert until fixed, or
  - Explicitly ignore/mark as ignored for cross-environment events.
- Decide whether one payment per order must be enforced in DB now. Recommendation: yes, use a unique constraint or transactional lock/recheck.
- Decide whether refund concurrency should be fixed now or deferred to remaining Phase 4 hardening. Recommendation: fix now because PR touched refund transaction boundaries.
- Decide whether role security should use URL matchers only or method security too. Current PR uses URL matchers; if keeping that strategy, include all vendor routes.

---

## Summary

`docs/glm-fix-plan-2026-06-13.md` remains the original audit fix plan. `docs/pr6-review-fix-prompts-2026-06-13.md` is now the specific PR #6 amend handoff for GLM. PR #6 is a good first pass and tests pass, but it should not be merged yet because the review found remaining webhook, payment transaction, refund transaction, and role-security issues. The project rules now explicitly require future PR review fix prompts to be saved under `docs/` to avoid missed context.