# Session 021 — PR #6 Amendment Complete

**Date:** 2026-06-13  
**Focus:** Implement all 9 fixes from docs/glm-amend-pr6-2026-06-13.md for PR #6

---

## What Was Done

All 9 fixes from the review document have been implemented:

### Fix 1 — Stripe webhook PostgreSQL idempotency (BLOCKER) ✅
- Added `StripeEventIdempotencyChecker` with `REQUIRES_NEW` transaction
- Uses `INSERT ... ON CONFLICT DO NOTHING` for safe duplicate detection
- Avoids PostgreSQL transaction abort on duplicate key error

### Fix 2 — Stripe webhook error handling (HIGH) ✅
- Made `Stripe-Signature` header optional and validate manually
- Maps missing/invalid signature to 400 Bad Request
- Only 5xx errors trigger Stripe retry

### Fix 3 — Missing Payment for supported Stripe events ✅
- Already documented as intentional (retry/alert pattern)
- Logs warning and throws to trigger Stripe retry

### Fix 4 — Payment creation unique constraint (BLOCKER) ✅
- Added Flyway migration V9 for unique constraint on `payments.order_id`
- `PaymentTransactionService` accepts `orderId`, not detached `Order` object
- Uses pessimistic locking (`SELECT FOR UPDATE`) on Order
- Duplicate check inside transaction after lock acquired

### Fix 5 — Refund lazy JPA graph outside transaction (BLOCKER) ✅
- Added `RefundContext` DTO to capture payment data transactionally
- `RefundService.loadRefundContext()` uses JOIN FETCH
- No lazy loading outside transaction

### Fix 6 — Full refund Order status persistence (HIGH) ✅
- Injected `OrderRepository` into `RefundTransactionService`
- Lock Payment with `findByIdForUpdate` (pessimistic lock)
- Explicitly save Order after calling `order.refund()`
- Removed incorrect cascade assumption

### Fix 7 — Payment-by-order authorization leak (HIGH) ✅
- Added `findByOrderIdAndOrderCustomerId` to `PaymentRepository`
- Returns 404 for both not-found and unauthorized
- Prevents information leak via 404 vs 422 difference

### Fix 8 — Refund amount race condition (HIGH) ✅
- Re-validates remaining amount AFTER acquiring lock
- Prevents over-refund when concurrent partial refunds

### Fix 9 — Vendor bookings endpoint role guard (HIGH) ✅
- Added explicit VENDOR role check for `GET /api/bookings/vendor`
- Must come BEFORE `/api/**` catch-all in `SecurityConfig`

---

## Test Results

```
Tests run: 284, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Files Changed

### New Files
- `src/main/java/com/hien/marketplace/application/dto/RefundContext.java`
- `src/main/java/com/hien/marketplace/infrastructure/stripe/StripeEventIdempotencyChecker.java`
- `src/main/resources/db/migration/V9__add_unique_constraint_payments_order_id.sql`

### Modified Files
- `src/main/java/com/hien/marketplace/application/service/PaymentService.java`
- `src/main/java/com/hien/marketplace/application/service/PaymentTransactionService.java`
- `src/main/java/com/hien/marketplace/application/service/RefundService.java`
- `src/main/java/com/hien/marketplace/application/service/RefundTransactionService.java`
- `src/main/java/com/hien/marketplace/config/SecurityConfig.java`
- `src/main/java/com/hien/marketplace/infrastructure/persistence/OrderRepository.java`
- `src/main/java/com/hien/marketplace/infrastructure/persistence/PaymentRepository.java`
- `src/main/java/com/hien/marketplace/infrastructure/persistence/stripe/StripeEventLogRepository.java`
- `src/main/java/com/hien/marketplace/infrastructure/stripe/StripeWebhookHandler.java`
- `src/main/java/com/hien/marketplace/interfaces/rest/StripeWebhookController.java`
- `src/test/java/com/hien/marketplace/application/service/RefundServiceTest.java`
- `src/test/java/com/hien/marketplace/integration/BookingControllerIntegrationTest.java`

---

## Current Project State

- **Branch:** `fix/phase4-audit-correctness`
- **PR:** #6 — `fix: Address audit correctness and security gaps`
- **PR URL:** https://github.com/FrorsttzNguyen/service-marketplace/pull/6
- **Commit:** `1bffa49` — fix: Address PR #6 review findings - correctness and security gaps
- **Merge status:** Ready for review. All blockers addressed. All tests pass.

---

## Next Steps

1. Review the PR diff one more time to verify all fixes
2. Merge PR #6 to main
3. Update Phase 4 learning docs to reflect the new transaction/security patterns
4. Continue to Phase 5 (Redis caching) or address any remaining Phase 4 documentation

---

## Paste-ready Prompt for Next Session

```text
We are in /Users/hiennguyen/Project/service-marketplace on branch fix/phase4-audit-correctness.

PR #6 has been amended with all 9 fixes from docs/glm-amend-pr6-2026-06-13.md.
All 284 tests pass.

Next steps:
1. Review the PR diff: gh pr diff 6
2. Merge PR #6: gh pr merge 6 --squash
3. Update Phase 4 learning docs to reflect new patterns

Read docs/session-notes/session-021.md for full context.
```
