# GLM Coder — Amend PR #6

**Verdict:** Amend PR #6 on the same branch. Do not merge yet.

**Branch:** `fix/phase4-audit-correctness`  
**PR:** #6 — https://github.com/FrorsttzNguyen/service-marketplace/pull/6  
**Base:** `main`

---

## Current state

- `./mvnw test` passes: 284 tests, 0 failures, 0 errors, 0 skipped.
- Tests do NOT cover transaction/security edge cases changed by this PR.
- Do not assume passing tests = safe to merge.

---

## Files already changed in PR #6

```
src/main/java/com/hien/marketplace/application/service/PaymentService.java
src/main/java/com/hien/marketplace/application/service/PaymentTransactionService.java
src/main/java/com/hien/marketplace/application/service/RefundService.java
src/main/java/com/hien/marketplace/application/service/RefundTransactionService.java
src/main/java/com/hien/marketplace/config/SecurityConfig.java
src/main/java/com/hien/marketplace/infrastructure/stripe/StripeWebhookHandler.java
src/main/java/com/hien/marketplace/interfaces/rest/PaymentController.java
src/main/java/com/hien/marketplace/interfaces/rest/StripeWebhookController.java
src/test/java/com/hien/marketplace/application/service/PaymentServiceTest.java
src/test/java/com/hien/marketplace/application/service/RefundServiceTest.java
```

---

## 9 fixes required

### Fix 1 — Stripe duplicate webhook: PostgreSQL-unsafe idempotency (BLOCKER)

**File:** `src/main/java/com/hien/marketplace/infrastructure/stripe/StripeWebhookHandler.java` ~line 94–110

**Problem:** Catching `DataIntegrityViolationException` inside `@Transactional` is not safe on PostgreSQL. A duplicate-key error aborts the transaction. A duplicate Stripe delivery can still return 500 and trigger Stripe retries. Also, catching all `DataIntegrityViolationException` hides non-duplicate integrity errors.

**Fix:** Replace with a native `INSERT ... ON CONFLICT DO NOTHING` query. If 0 rows inserted → duplicate → return cleanly without entering a failed transaction state.

Add to `StripeEventLogRepository`:

```java
@Modifying
@Query(value = """
    INSERT INTO stripe_event_log (stripe_event_id, event_type, processed_at)
    VALUES (:eventId, :eventType, NOW())
    ON CONFLICT (stripe_event_id) DO NOTHING
    """, nativeQuery = true)
int insertIfNotExists(String eventId, String eventType);
```

In handler — run this insert in a SEPARATE inner transaction (new `@Transactional(propagation = REQUIRES_NEW)` method on a separate Spring bean or component) BEFORE entering the main `@Transactional processEvent`:

```java
// separate @Transactional(propagation = REQUIRES_NEW) component:
int inserted = eventLogRepository.insertIfNotExists(eventId, eventType);
if (inserted == 0) {
    log.info("Event {} already processed, skipping", eventId);
    return; // clean 200, no failed transaction
}
```

**Tests required:**
- Duplicate event ID returns 200 cleanly and does not call PaymentService again.
- Non-duplicate DB integrity failure is not swallowed as duplicate.

---

### Fix 2 — Stripe webhook: malformed payload/header returns 500 instead of 400 (HIGH)

**File:** `src/main/java/com/hien/marketplace/interfaces/rest/StripeWebhookController.java` ~line 88–115

**Problem:** Only `StripeApiException` maps to 400. Missing `Stripe-Signature` header, invalid signature, and malformed JSON/parse failures hit the generic `Exception` branch and return 500. Stripe retries permanently bad requests forever.

**Fix:** Make header optional, validate manually, map parse/signature failures to 400:

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
    } catch (StripeApiException e) {
        return ResponseEntity.badRequest().build();
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
```

Only catch `StripeApiException` as 400 — do not over-catch domain exceptions.

**Tests required:**
- Missing `Stripe-Signature` → 400.
- Invalid signature → 400.
- Malformed payload → 400.
- Valid event + domain failure → 500.

---

### Fix 3 — Missing local Payment for supported Stripe events causes infinite retry (HIGH)

**Files:**
- `src/main/java/com/hien/marketplace/infrastructure/stripe/StripeWebhookHandler.java` ~line 151–153, 185–187
- `src/main/java/com/hien/marketplace/application/service/PaymentService.java` ~line 145–149, 179–183

**Problem:** If `payment_intent.succeeded` or `payment_intent.payment_failed` arrives but no local Payment exists, `PaymentService` throws `ResourceNotFoundException`, the event log rolls back, controller returns 500, and Stripe retries forever.

**Fix:** Choose ONE option and document it in a comment:

- **Option A (retry/alert):** Keep throwing, add explicit log message, add test documenting this is intentional.
- **Option B (ignore/mark):** Catch `ResourceNotFoundException` for these events, log as WARN with event ID, mark event as processed so Stripe stops retrying. Document why (cross-environment, stale data).

**Tests required:** Cover chosen behavior explicitly.

---

### Fix 4 — Payment creation: detached Order, no DB unique constraint (BLOCKER)

**Files:**
- `src/main/java/com/hien/marketplace/application/service/PaymentService.java` ~line 81–117
- `src/main/java/com/hien/marketplace/application/service/PaymentTransactionService.java` ~line 50–64
- `src/main/resources/db/migration/V5__create_orders_and_payments.sql` ~line 41–42

**Problem:**
1. Duplicate check (`paymentRepository.existsByOrderId`) happens before Stripe call and outside the write transaction.
2. `payments.order_id` has an index only, not a UNIQUE constraint — DB does not enforce one payment per order.
3. The `Order` loaded before the Stripe call is reused later — order state may change while Stripe call is in progress.
4. Two concurrent requests can both pass the duplicate check, both create Stripe PaymentIntents, both insert local Payment rows.

**Fix:**

Add a new Flyway migration (do NOT edit already-applied V5):

```sql
-- Vx__add_unique_constraint_payments_order_id.sql
ALTER TABLE payments ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);
```

Change `PaymentTransactionService` to accept IDs, not objects:

```java
@Transactional
public Payment createPaymentWithOrderUpdate(Long userId, Long orderId,
        String paymentIntentId, String paymentMethod) {
    Order order = orderRepository.findByIdForUpdate(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    if (!order.getCustomer().getId().equals(userId)) {
        throw new AccessDeniedException("Not your order");
    }
    if (!order.getStatus().equals(OrderStatus.CREATED)) {
        throw new BusinessRuleViolationException("Order not in CREATED state");
    }
    if (paymentRepository.existsByOrderId(orderId)) {
        throw new BusinessRuleViolationException("Payment already exists for order");
    }
    Payment payment = new Payment(order, order.getTotal(), paymentIntentId, paymentMethod);
    order.markAsPendingPayment();
    orderRepository.save(order);
    return paymentRepository.save(payment);
}
```

Add to `OrderRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select o from Order o where o.id = :orderId")
Optional<Order> findByIdForUpdate(Long orderId);
```

**Tests required:**
- Transaction service rejects duplicate payment inserted concurrently.
- Transaction service rejects order with status != CREATED.

---

### Fix 5 — Refund: lazy JPA graph accessed outside transaction (BLOCKER)

**Files:**
- `src/main/java/com/hien/marketplace/application/service/RefundService.java` ~line 69–98, 153–157
- `src/main/java/com/hien/marketplace/domain/payment/Payment.java` ~line 35–37, 70–71
- `src/main/resources/application.yml` line 16 (`open-in-view: false`)

**Problem:** `createRefund()` is not `@Transactional`. `payment.getOrder().getCustomer()` and `payment.getRefunds()` are lazy. With `open-in-view: false`, accessing them on a detached Payment throws `LazyInitializationException` before Stripe is even called.

**Fix:** Do NOT make the whole `createRefund()` transactional (Stripe call must stay outside DB transaction). Instead:

1. Add a transactional read method that loads all needed data and returns a snapshot DTO:

```java
@Transactional(readOnly = true)
public RefundContext loadRefundContext(Long paymentId, Long userId) {
    Payment payment = paymentRepository.findByIdWithOrderAndRefunds(paymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    if (!payment.getOrder().getCustomer().getId().equals(userId)) {
        throw new AccessDeniedException("Not your payment");
    }
    return new RefundContext(
        payment.getId(),
        payment.getPaymentIntentId(),
        payment.getAmount(),
        payment.getOrder().getId(),
        payment.getOrder().getStatus(),
        calculateAlreadyRefunded(payment.getRefunds())
    );
}
```

Add to `PaymentRepository`:

```java
@Query("select p from Payment p join fetch p.order o join fetch o.customer join fetch p.refunds where p.id = :id")
Optional<Payment> findByIdWithOrderAndRefunds(Long id);
```

2. Use the snapshot DTO for validation and Stripe call — no lazy access.
3. Final write goes through `RefundTransactionService` which reloads/locks Payment.

**Tests required:**
- Refund creation with `open-in-view=false` does not throw `LazyInitializationException`.
- Do not mock away the transaction path.

---

### Fix 6 — Full refund: OrderStatus.REFUNDED may not persist (HIGH)

**File:** `src/main/java/com/hien/marketplace/application/service/RefundTransactionService.java` ~line 55–72  
**File:** `src/main/java/com/hien/marketplace/domain/payment/Payment.java` ~line 35–37

**Problem:** Current code:

```java
if (isFullRefund) {
    payment.getOrder().refund();
    paymentRepository.save(payment); // comment says "Cascades to order"
}
```

`Payment.order` is `@ManyToOne` with no `cascade`. There is no cascade from Payment to Order. The comment is wrong. If `order` is detached, `order.refund()` state change is NOT persisted.

**Fix:**

1. Inject `OrderRepository` into `RefundTransactionService`.
2. Reload managed `Payment` inside transaction using `findByIdForUpdate` (pessimistic lock).
3. Navigate to order, call `order.refund()`, explicitly save via `orderRepository.save(order)`.
4. Remove incorrect cascade comment.

```java
@Transactional
public Refund createRefundWithOrderUpdate(Long paymentId, Money refundAmount,
        String reason, String stripeRefundId) {
    Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(...);
    Order order = payment.getOrder();
    boolean isFullRefund = refundAmount.equals(payment.getAmount());
    Refund refund = new Refund(payment, refundAmount, reason, stripeRefundId);
    refundRepository.save(refund);
    if (isFullRefund) {
        order.refund();
        orderRepository.save(order);
    }
    return refund;
}
```

**Tests required:**
- Full refund persists `OrderStatus.REFUNDED`.
- Partial refund does NOT change order status.

---

### Fix 7 — Payment-by-order: leaks existence via 404 vs 422 (HIGH)

**Files:**
- `src/main/java/com/hien/marketplace/application/service/PaymentService.java` ~line 225–237
- `src/main/java/com/hien/marketplace/interfaces/rest/PaymentController.java` ~line 128–144

**Problem:** Current logic:
- No payment found → 404.
- Payment exists but belongs to someone else → `BusinessRuleViolationException` → 422.

This leaks whether another customer's order has a payment (404 vs 422 response difference).

**Fix:** Query by both `orderId` AND `customerId`. Unauthorized and not-found look identical (both 404):

Add to `PaymentRepository`:

```java
Optional<Payment> findByOrderIdAndOrderCustomerId(Long orderId, Long customerId);
```

Service:

```java
@Transactional(readOnly = true)
public Optional<Payment> getPaymentByOrderId(Long userId, Long orderId) {
    return paymentRepository.findByOrderIdAndOrderCustomerId(orderId, userId);
}
```

Remove the ownership check inside `map()`. Controller returns 404 for both missing and unauthorized — document this policy.

**Tests required:**
- Owner gets payment (200).
- Non-owner gets 404 (not 422).
- API behavior matches controller docs.

---

### Fix 8 — Refund amount: race condition allows over-refund (HIGH)

**Files:**
- `src/main/java/com/hien/marketplace/application/service/RefundService.java` ~line 96–115, 130–157
- `src/main/java/com/hien/marketplace/infrastructure/persistence/PaymentRepository.java`

**Problem:** Pre-validation of remaining refundable amount happens before Stripe call. Final insert is in a separate transaction. Two concurrent partial refunds can both pass validation, both hit Stripe, and combined amount exceeds original payment.

**Fix:**

1. Keep pre-validation before Stripe call for UX (fast fail).
2. Inside `RefundTransactionService`, after `findByIdForUpdate` (pessimistic lock), RE-VALIDATE remaining amount before inserting the refund row.

```java
// inside RefundTransactionService, after acquiring lock:
Money alreadyRefunded = calculateAlreadyRefunded(payment.getRefunds());
Money remaining = payment.getAmount().subtract(alreadyRefunded);
if (refundAmount.isGreaterThan(remaining)) {
    throw new BusinessRuleViolationException("Refund amount exceeds remaining refundable");
}
```

Add to `PaymentRepository` if not already present:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Payment p where p.id = :paymentId")
Optional<Payment> findByIdForUpdate(Long paymentId);
```

**Tests required:**
- Re-validation inside transaction rejects over-refund when another refund was inserted between pre-check and final write.

---

### Fix 9 — Role guard: GET /api/bookings/vendor missing VENDOR check (HIGH)

**File:** `src/main/java/com/hien/marketplace/config/SecurityConfig.java` ~line 78–85  
**File:** `src/main/java/com/hien/marketplace/interfaces/rest/BookingController.java` ~line 84–97

**Problem:** SecurityConfig guards `/api/vendor/**` but the vendor bookings endpoint is:

```
GET /api/bookings/vendor
```

This falls through to `.requestMatchers("/api/**").authenticated()` — a CUSTOMER token can reach it and get a service-layer 422 instead of being blocked at 403.

**Fix:** Add explicit matcher BEFORE the `/api/**` catch-all:

```java
.requestMatchers(HttpMethod.GET, "/api/bookings/vendor").hasRole("VENDOR")
```

**Tests required:**
- CUSTOMER token → `GET /api/bookings/vendor` returns 403.
- VENDOR token → can access endpoint.
- Customer booking routes still work for CUSTOMER role.

---

## Rules for GLM

- **Surgical** — touch only what is described above.
- **Tests** — add/update tests for every changed behavior.
- **No new PR** — push to `fix/phase4-audit-correctness`, amend the same PR #6.
- **After all fixes:** run `./mvnw test` and report exact result (tests run / failures / errors / skipped).
- **Report:** list every file changed with one-line reason.
