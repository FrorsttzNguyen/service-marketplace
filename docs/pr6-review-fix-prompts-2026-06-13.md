# PR #6 Review Follow-up — Fix Prompts for Coder Agent

**Date:** 2026-06-13  
**PR:** #6 — `fix: Address audit correctness and security gaps`  
**Branch:** `fix/phase4-audit-correctness`  
**URL:** https://github.com/FrorsttzNguyen/service-marketplace/pull/6  
**Review result:** Do **not** merge yet. Tests pass, but blockers remain.

---

## 0. Short Verdict for Hien to Send GLM

```text
Amend PR #6 on the same branch. Do not merge yet.
```

PR #6 is moving in the correct direction, but it is still a partial fix. The two highest-risk remaining areas are:

1. **Stripe webhook duplicate transaction handling on PostgreSQL** — duplicate webhook delivery must return clean `200 OK` without relying on a caught duplicate-key exception inside an aborted transaction.
2. **Payment/Refund transaction boundaries** — current code still passes detached entities and touches lazy JPA graphs outside safe transactional boundaries.

Use the full copy-paste prompt in [Section 4](#4-copy-paste-prompt-for-coder-agent) for the coder agent.

---

## 1. Verification Already Run

Command:

```bash
./mvnw test
```

Result:

```text
Tests run: 284, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Important: passing tests are not enough here because the current tests do not cover several transaction/security paths changed by PR #6.

---

## 2. High-level Review Verdict

PR #6 is a good first pass for Tasks 1–5 from `docs/glm-fix-plan-2026-06-13.md`, but it is still incomplete.

Main remaining risks:

1. Stripe webhook duplicate handling is still not PostgreSQL-safe.
2. Stripe webhook 400/500 split is incomplete.
3. Missing local Payment for supported Stripe events can create permanent retry loops.
4. Payment creation still has a concurrency/stale-entity problem.
5. Refund creation still accesses lazy JPA relationships outside a transaction.
6. Full refund may not persist `OrderStatus.REFUNDED`.
7. Payment-by-order authorization leaks existence and returns the wrong status.
8. Refund amount validation remains race-prone.
9. Role guard misses `GET /api/bookings/vendor`.

---

## 3. Findings and Required Fixes

### Finding 1 — Duplicate Stripe webhook handling can still return 500

**Severity:** Blocker — can make duplicate Stripe deliveries return 500 and trigger retries.  
**File/lines:** `src/main/java/com/hien/marketplace/infrastructure/stripe/StripeWebhookHandler.java:94-110`

Current PR shape:

```java
@Transactional
public void processEvent(Event event) {
    try {
        eventLogRepository.saveAndFlush(new StripeEventLog(eventId, eventType));
    } catch (DataIntegrityViolationException e) {
        log.info("Event {} already processed, skipping", eventId);
        return;
    }

    switch (eventType) { ... }
}
```

**Problem:** On PostgreSQL, a duplicate-key error can abort the current transaction. Catching `DataIntegrityViolationException` inside the same transaction does not guarantee the transaction can commit cleanly. A duplicate Stripe webhook should return `200 OK`, but this pattern can still end in `UnexpectedRollbackException`/500.

Also, the catch treats **all** `DataIntegrityViolationException` as duplicate event ID. That hides non-duplicate integrity problems.

**Required fix direction:**

Use a safer idempotency strategy. Acceptable options:

Option A — explicit pre-check + insert:

```java
if (eventLogRepository.existsById(eventId)) {
    log.info("Event {} already processed, skipping", eventId);
    return;
}

try {
    eventLogRepository.saveAndFlush(new StripeEventLog(eventId, eventType));
} catch (DataIntegrityViolationException e) {
    if (eventLogRepository.existsById(eventId)) {
        log.info("Event {} already processed by concurrent request, skipping", eventId);
        return;
    }
    throw e;
}
```

But note: even with this, if PostgreSQL aborts the transaction after the failed insert, the catch path may still be unsafe unless the duplicate insert happens in a separate transaction or the code avoids causing the duplicate insert in the common path.

Option B — preferred for PostgreSQL correctness:

- Put the “claim event ID” operation in a separate transactional component/method.
- Use `REQUIRES_NEW` or a native `INSERT ... ON CONFLICT DO NOTHING` query that returns whether the insert happened.
- If not inserted because conflict, return `200 OK` without entering a failed transaction state.

Example repository native query idea:

```java
@Modifying
@Query(value = """
    INSERT INTO stripe_event_log (stripe_event_id, event_type, processed_at)
    VALUES (:eventId, :eventType, NOW())
    ON CONFLICT (stripe_event_id) DO NOTHING
    """, nativeQuery = true)
int insertIfNotExists(String eventId, String eventType);
```

Then:

```java
int inserted = eventLogRepository.insertIfNotExists(eventId, eventType);
if (inserted == 0) {
    return; // duplicate, clean 200 path
}
```

**Required tests:**

- Duplicate event returns clean success and does not call `PaymentService` again.
- Non-duplicate DB integrity failure is not mislabeled as duplicate.
- If supported event processing fails, event is not treated as processed.

---

### Finding 2 — Webhook malformed payload/header can return 500

**Severity:** High — permanently bad webhook requests can become retrying 500s instead of terminal 400s.  
**File/lines:** `src/main/java/com/hien/marketplace/interfaces/rest/StripeWebhookController.java:88-115`

Current PR shape:

```java
} catch (StripeApiException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
} catch (Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
}
```

**Problem:** Comments say malformed payload should return 400, but only `StripeApiException` maps to 400. Missing `Stripe-Signature` header, malformed JSON, or parsing failures can hit the generic `Exception` branch and return 500. Stripe will retry permanently bad requests.

**Required fix direction:**

- Keep valid domain-processing failures as 500.
- Map invalid signature, missing signature header, and malformed/unparseable payload to 400.
- Consider catching Spring's `MissingRequestHeaderException` globally or making header optional and validating manually.

Example controller direction:

```java
public ResponseEntity<Void> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader
) {
    if (sigHeader == null || sigHeader.isBlank()) {
        return ResponseEntity.badRequest().build();
    }

    try {
        Event event = webhookHandler.verifySignature(payload, sigHeader);
        webhookHandler.processEvent(event);
        return ResponseEntity.ok().build();
    } catch (StripeApiException | IllegalArgumentException e) {
        return ResponseEntity.badRequest().build();
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
```

Only use `IllegalArgumentException` for payload parsing if verified from Stripe SDK behavior; do not over-catch domain exceptions as 400.

**Required tests:**

- Missing `Stripe-Signature` returns 400.
- Invalid signature returns 400.
- Malformed payload returns 400.
- Valid event + domain failure returns 500.

---

### Finding 3 — Missing local Payment for supported Stripe events can retry forever

**Severity:** High — valid-but-unmatchable Stripe events can create endless retry noise unless behavior is intentional and tested.  
**File/lines:**

- `src/main/java/com/hien/marketplace/infrastructure/stripe/StripeWebhookHandler.java:151-153`, `:185-187`
- `src/main/java/com/hien/marketplace/application/service/PaymentService.java:145-149`, `:179-183`

Current PR behavior:

- `payment_intent.succeeded` / `payment_intent.payment_failed` calls `PaymentService`.
- If local Payment is missing, `PaymentService` throws `ResourceNotFoundException`.
- Handler lets it escape.
- Event log rolls back.
- Controller returns 500.
- Stripe retries.

**Why this may be wrong:** If the event is from another environment, stale data, deleted local row, or a test-mode/prod mismatch, retry will never succeed. Stripe will keep retrying and logs will churn.

**Required decision:** GLM must not guess silently. Pick and document one of these:

Option A — retry/alert:

- Keep 500 for missing Payment.
- Add clear log/monitoring message.
- Add test documenting this behavior.
- Explain this is intentional because local Payment should always exist for supported events.

Option B — ignored/mismatched event:

- Treat missing Payment as an ignored event, but mark it explicitly as ignored/processed to avoid endless retry.
- This ideally needs `stripe_event_log` to store status (`PROCESSED`, `IGNORED`, `FAILED`) in a later migration.
- If not adding status now, at least document the limitation clearly.

**Required tests:**

- Missing Payment behavior is covered by test and matches documented decision.

---

### Finding 4 — Payment creation still has concurrency/stale-entity risk

**Severity:** Blocker — can create duplicate local payments / orphan Stripe intents or accept stale order state.  
**File/lines:**

- `src/main/java/com/hien/marketplace/application/service/PaymentService.java:81-117`
- `src/main/java/com/hien/marketplace/application/service/PaymentTransactionService.java:50-64`
- `src/main/resources/db/migration/V5__create_orders_and_payments.sql:41-42`

Current PR flow:

```java
Order order = orderRepository.findById(orderId).orElseThrow(...);
if (paymentRepository.existsByOrderId(orderId)) throw ...;
PaymentIntent paymentIntent = stripeClient.createPaymentIntent(order.getTotal(), orderId);
paymentTransactionService.createPaymentWithOrderUpdate(order, paymentIntent.getId(), paymentMethod);
```

Inside transaction service:

```java
public Payment createPaymentWithOrderUpdate(Order order, ...) {
    Payment payment = new Payment(order, order.getTotal());
    paymentRepository.save(payment);
    order.markAsPendingPayment();
    orderRepository.save(order);
}
```

**Problems:**

1. Duplicate check happens before Stripe call and outside the transactional write.
2. `payments.order_id` has an index, not a unique constraint.
3. The `Order` object is loaded before the Stripe call and reused later; order state may change while Stripe call is in progress.

**Failure scenario:** Two concurrent requests for the same order can both see no payment, both create Stripe PaymentIntents, and both insert local Payment rows. Or an order can be cancelled after the pre-check but before the transactional write.

**Required fix direction:**

- In `PaymentTransactionService`, reload the order inside the transaction.
- Lock it pessimistically or use optimistic versioning if available.
- Re-check ownership/status/duplicate payment inside the transaction.
- Add a DB invariant if one payment per order is true:

```sql
ALTER TABLE payments ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);
```

Since Flyway migrations are versioned, add a new migration, not editing old migration if already applied.

Possible repository additions:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select o from Order o where o.id = :orderId")
Optional<Order> findByIdForUpdate(Long orderId);
```

Then service method should accept `userId`, `orderId`, not detached `Order`:

```java
@Transactional
public Payment createPaymentWithOrderUpdate(Long userId, Long orderId, String paymentIntentId, String paymentMethod) {
    Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(...);
    // re-check owner/status/duplicate here
    ...
}
```

**Required tests:**

- Transaction service rejects duplicate payment if one already exists.
- Transaction service rejects order if status changed from CREATED.
- Optional PostgreSQL/TestContainers test for unique `payments.order_id`.

---

### Finding 5 — Refund create flow accesses lazy JPA graph outside transaction

**Severity:** Blocker — refund creation can fail before Stripe call with `LazyInitializationException` under `open-in-view=false`.  
**File/lines:**

- `src/main/java/com/hien/marketplace/application/service/RefundService.java:69-98`, `:153-157`
- `src/main/java/com/hien/marketplace/domain/payment/Payment.java:35-37`, `:70-71`
- `src/main/resources/application.yml:16`

Current PR flow:

```java
public Refund createRefund(...) {
    Payment payment = paymentRepository.findById(paymentId).orElseThrow(...);

    if (!payment.getOrder().getCustomer().getId().equals(userId)) { ... }

    validateRefundAmount(payment, refundAmount);

    stripeClient.createRefund(...);

    refundTransactionService.createRefundWithOrderUpdate(payment, ...);
}
```

`Payment.order` is lazy, `Payment.refunds` is lazy, and common config has:

```yaml
spring.jpa.open-in-view: false
```

**Problem:** `createRefund()` is not transactional. The `Payment` returned by repository can be detached before accessing `payment.getOrder()` and `payment.getRefunds()`. This can throw `LazyInitializationException` before Stripe is called.

**Required fix direction:**

Do not just annotate all `createRefund()` with `@Transactional`, because Stripe call should stay outside DB transaction.

Instead:

1. Add a read/validation transactional method that loads all required fields safely, or use repository query/entity graph/projection.
2. Return a snapshot DTO needed for Stripe call: payment intent ID, amount, existing refunded total, owner ID/status.
3. Or move validation into `RefundTransactionService` and ensure no Stripe call happens while DB transaction is open.

Possible pattern:

- `RefundPreparationService` or method in transaction service:
  - Load payment with order/customer/refunds using entity graph or queries.
  - Validate owner/status/amount.
  - Return immutable data needed for Stripe.
- Call Stripe outside transaction.
- Final transaction reloads/locks payment and re-validates remaining refundable amount before insert.

**Required tests:**

- Test under `open-in-view=false` that refund creation does not throw lazy init.
- Test should not mock away the transaction service path only.

---

### Finding 6 — Full refund order update may not persist

**Severity:** High — local refund can succeed while `OrderStatus.REFUNDED` is not reliably persisted.  
**File/lines:**

- `src/main/java/com/hien/marketplace/application/service/RefundTransactionService.java:55-72`
- `src/main/java/com/hien/marketplace/domain/payment/Payment.java:35-37`

Current PR code:

```java
if (isFullRefund) {
    payment.getOrder().refund();
    paymentRepository.save(payment); // Cascades to order
}
```

**Problem:** `Payment.order` is:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "order_id", nullable = false)
private Order order;
```

There is no cascade from `Payment` to `Order`. The comment “Cascades to order” is incorrect. If `payment` is detached or `order` is lazy/detached, this can either fail or not persist the order state transition.

**Required fix direction:**

- Inject `OrderRepository` into `RefundTransactionService`.
- Reload managed `Payment` and/or `Order` inside the transaction.
- Call `order.refund()` on managed `Order`.
- Save `orderRepository.save(order)` explicitly if needed.
- Remove incorrect cascade comment.

Better method signature should avoid passing detached `Payment`:

```java
@Transactional
public Refund createRefundWithOrderUpdate(Long paymentId, Money refundAmount, String reason, String stripeRefundId) {
    Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(...);
    Order order = payment.getOrder();
    ...
    if (isFullRefund) {
        order.refund();
        orderRepository.save(order);
    }
}
```

**Required tests:**

- Unit/integration test for `RefundTransactionService` itself.
- Full refund persists order status `REFUNDED`.
- Partial refund does not change order status.

---

### Finding 7 — Payment-by-order authorization leaks existence and returns wrong status

**Severity:** High — response difference can reveal whether another customer's order has a payment.  
**File/lines:**

- `src/main/java/com/hien/marketplace/application/service/PaymentService.java:225-237`
- `src/main/java/com/hien/marketplace/interfaces/rest/PaymentController.java:128-144`
- `src/main/java/com/hien/marketplace/interfaces/rest/GlobalExceptionHandler.java:107-120`

Current PR code:

```java
return paymentRepository.findByOrderId(orderId)
    .map(payment -> {
        if (!payment.getOrder().getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException(...);
        }
        return payment;
    });
```

**Problems:**

- No payment → Optional.empty → controller returns 404.
- Someone else's payment exists → `BusinessRuleViolationException` → global handler returns 422.
- Controller docs say unauthorized is 403.
- This leaks whether a guessed order has a payment.

**Required fix direction:**

Preferred: query by both order ID and customer ID, so unauthorized and missing look the same if desired.

Repository option:

```java
Optional<Payment> findByOrderIdAndOrderCustomerId(Long orderId, Long customerId);
```

Then service:

```java
@Transactional(readOnly = true)
public Optional<Payment> getPaymentByOrderId(Long userId, Long orderId) {
    return paymentRepository.findByOrderIdAndOrderCustomerId(orderId, userId);
}
```

If Hien wants strict forbidden when order exists but belongs to someone else:

- First check order ownership, throw `AccessDeniedException` for non-owner.
- Then fetch payment.
- But note this can still reveal order existence unless handled carefully.

For security simplicity, returning 404 for “not found or not yours” is acceptable if documented.

**Required tests:**

- Owner gets payment.
- Non-owner does not get payment.
- Response code is consistent with chosen policy.
- API docs match behavior.

---

### Finding 8 — Refund amount validation remains race-prone

**Severity:** High — concurrent partial refunds can exceed the original payment amount.  
**File/lines:**

- `src/main/java/com/hien/marketplace/application/service/RefundService.java:96-115`, `:130-157`
- `src/main/java/com/hien/marketplace/infrastructure/persistence/PaymentRepository.java:10-33`

Current PR behavior:

- Calculates already refunded amount before Stripe call.
- Inserts refund later in separate transaction.
- No lock/versioning protects the read-then-write sequence.

**Failure scenario:** Two partial refunds run concurrently. Both read the same existing refunded total, both pass validation, both create Stripe refunds, and combined refund amount exceeds original payment.

**Required fix direction:**

At minimum:

- Re-validate remaining refundable amount inside the same transaction as refund insert.
- Lock the payment row or use payment `@Version`.
- Ideally use a pessimistic lock on `Payment` during final refund insert.

Potential repository method:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Payment p where p.id = :paymentId")
Optional<Payment> findByIdForUpdate(Long paymentId);
```

Then final transaction:

```java
Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(...);
validateRefundAmount(payment, refundAmount); // inside transaction, after lock
save refund
```

**Important Stripe complication:** If final DB validation fails after Stripe refund already succeeded, local and Stripe state diverge. To reduce this, do pre-validation before Stripe for UX, then re-validation after Stripe for safety, and document/or handle compensating case.

**Required tests:**

- Re-validation rejects over-refund if another refund appears between pre-check and final insert.
- Optional concurrency test later with PostgreSQL/TestContainers.

---

### Finding 9 — Role security misses vendor bookings endpoint

**Severity:** High — CUSTOMER requests can pass URL security and reach vendor-only controller logic.  
**File/lines:**

- `src/main/java/com/hien/marketplace/config/SecurityConfig.java:78-85`
- `src/main/java/com/hien/marketplace/interfaces/rest/BookingController.java:84-97`

PR currently protects:

```java
.requestMatchers("/api/vendor/**").hasRole("VENDOR")
```

But vendor bookings endpoint is:

```java
@RestController
@RequestMapping("/api/bookings")
class BookingController {
    @GetMapping("/vendor")
    public ResponseEntity<Page<BookingResponse>> getVendorBookings(...)
}
```

So route is:

```text
GET /api/bookings/vendor
```

This falls through to:

```java
.requestMatchers("/api/**").authenticated()
```

**Problem:** CUSTOMER can reach the controller and get service-layer 422 instead of being blocked as 403 at security layer.

**Required fix direction:**

Add explicit matcher before `/api/**`:

```java
.requestMatchers(HttpMethod.GET, "/api/bookings/vendor").hasRole("VENDOR")
```

If there are other vendor-only booking operations later, include them too.

**Required tests:**

- CUSTOMER token calling `GET /api/bookings/vendor` returns 403.
- VENDOR token can reach endpoint.
- Customer booking routes still work for CUSTOMER.

---

## 4. Copy-paste Prompt for Coder Agent

Use this prompt for GLM/coder agent:

```text
Read docs/pr6-review-fix-prompts-2026-06-13.md fully before coding.

You are amending PR #6 on branch fix/phase4-audit-correctness. Do not create a new PR.

Current status:
- ./mvnw test passes: 284 tests, 0 failures/errors/skips.
- However, review found remaining correctness/security blockers. Do not merge until fixed.

Fix these issues surgically:

1. Stripe duplicate webhook handling
   - Current saveAndFlush + catch DataIntegrityViolationException inside @Transactional is not PostgreSQL-safe.
   - Duplicate webhook delivery must return 200 cleanly.
   - Do not treat every DataIntegrityViolationException as duplicate.
   - Prefer INSERT ... ON CONFLICT DO NOTHING or an isolated claim-event strategy.

2. Stripe webhook 400 vs 500
   - Invalid signature, missing Stripe-Signature header, malformed payload/parse failure should return 400.
   - Valid webhook with domain-processing failure should return 500.
   - Add tests for both paths.

3. Missing local Payment for supported Stripe events
   - Decide and document behavior: retry/alert vs explicitly ignore/mark stale/cross-environment event.
   - Add tests for chosen behavior.

4. Payment creation transaction
   - Do not pass detached/stale Order into PaymentTransactionService.
   - Reload/lock/recheck Order inside transaction.
   - Re-check duplicate payment inside transaction.
   - Add DB unique constraint on payments.order_id if one payment per order is invariant.
   - Add tests for duplicate guard and status recheck.

5. Refund create flow lazy access
   - Do not access payment.getOrder() or payment.getRefunds() from detached Payment outside transaction.
   - Use transactional read/entity graph/projection and final transactional revalidation.
   - Keep Stripe call outside DB transaction.

6. Full refund order update
   - Remove incorrect 'Cascades to order' comment.
   - Reload managed Payment/Order inside transaction and explicitly save Order via OrderRepository if needed.
   - Add tests for RefundTransactionService full and partial refund behavior.

7. Payment-by-order authorization
   - Avoid leaking payment existence by 404 vs 422.
   - Either query by orderId + customerId and return 404 for not yours/not found, or throw proper 403 consistently.
   - Update controller docs/tests to match chosen policy.

8. Refund amount race
   - Re-validate remaining refundable amount inside the same transaction as refund insert.
   - Use lock/versioning if needed.
   - Add test where another refund appears between precheck and final insert.

9. Role security
   - Current SecurityConfig only guards /api/vendor/**.
   - Also guard GET /api/bookings/vendor as VENDOR before /api/** authenticated matcher.
   - Add MockMvc/security test: CUSTOMER gets 403, VENDOR can access.

Rules:
- Keep changes surgical.
- Add/update tests for behavior changes.
- Run ./mvnw test after changes.
- Report files changed and exact test result.
```

---

## 5. Review Checklist After Coder Amends PR #6

Before merge, reviewer should verify:

- [ ] Duplicate Stripe event returns 200 cleanly on PostgreSQL-safe path.
- [ ] Invalid/malformed webhook returns 400.
- [ ] Valid webhook domain failure returns 500.
- [ ] Missing local Payment behavior is documented and tested.
- [ ] Payment transaction reloads/locks/rechecks order and duplicate payment.
- [ ] DB enforces one payment per order if that is the invariant.
- [ ] Refund validation avoids detached lazy graph access.
- [ ] Full refund persists `OrderStatus.REFUNDED` reliably.
- [ ] Payment-by-order unauthorized access does not leak via 404 vs 422.
- [ ] Refund final insert revalidates remaining refundable amount.
- [ ] `GET /api/bookings/vendor` requires VENDOR role.
- [ ] `./mvnw test` passes.

---

## 6. If Time Is Limited

Minimum blockers before merge:

1. Fix Stripe duplicate webhook handling.
2. Fix payment creation transactional duplicate/stale order issue.
3. Fix refund lazy access + full refund order persistence.
4. Fix `GET /api/bookings/vendor` role guard.
5. Add tests for the above.

Other items can be explicitly deferred only if documented in the PR and follow-up task list.
