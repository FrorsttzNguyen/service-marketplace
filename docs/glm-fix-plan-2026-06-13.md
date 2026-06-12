# GLM Fix Plan — Phase 0–4 Audit Follow-up

**Date:** 2026-06-13  
**Owner:** Hien  
**Purpose:** A precise handoff for GLM coder to fix the highest-risk findings from the full project audit.  
**Source context:** `docs/project-audit-2026-06-13.md`, `docs/session-notes/session-018.md`, `docs/session-notes/session-019.md`.

---

## 0. Executive Summary

Phase 0–4 are strong as a Java/Spring learning backend, but several claims in the docs/evaluation are ahead of the actual implementation. The next work should be **correctness/security first**, documentation polish second.

The safest operating model:

1. Create one branch for the whole audit-fix stream.
2. Fix in small commits, ideally one issue per commit.
3. Open one PR when P0/P1 fixes and tests are ready.
4. Keep HTML/README docs as later commits in the same PR only if code fixes are already stable.

Recommended branch:

```bash
git checkout -b fix/phase4-audit-correctness
```

Recommended PR title:

```text
fix: Address audit correctness and security gaps
```

Recommended PR body summary:

```markdown
## Summary
- Fix Stripe webhook idempotency so failed domain updates are retried instead of marked processed
- Fix payment/order authorization leak
- Resolve vendor profile by authenticated userId instead of assuming userId == vendorId
- Enforce vendor/admin role checks consistently
- Wire ServiceSpecification into a real public search API
- Add PostgreSQL/TestContainers coverage for critical database behavior
- Harden booking overlap protection at the database level
- Repair docs/navigation after implementation changes

## Tests
- ./mvnw test
- PostgreSQL/TestContainers tests for migrations, webhook idempotency, service search, and booking overlap
```

---

## 1. Priority Order

Do **not** start with docs. Fix behavior first, because docs should explain the final correct implementation.

| Order | Priority | Task | Why |
|---:|---|---|---|
| 1 | P0 | Fix Stripe webhook idempotency/transaction semantics | Current code can mark a Stripe event processed even if domain update failed |
| 2 | P0 | Fix `@Transactional protected` self-invocation in payment/refund flows | Comments promise atomic DB updates, but Spring proxy transaction does not apply to self-invoked protected methods |
| 3 | P0 | Fix payment-by-order authorization leak | Authenticated user can fetch another user's payment if they know `orderId` |
| 4 | P1 | Fix vendor `userId == vendorId` assumption | User ID and vendor profile ID are separate DB identities |
| 5 | P1 | Enforce role-based access consistently | Code comments claim `@PreAuthorize`, but source has no real method-level role guards |
| 6 | P1 | Wire `ServiceSpecification` into API | Dynamic filters exist but are not reachable from controllers |
| 7 | P1/P2 | Add PostgreSQL/TestContainers tests | Current main test profile is H2 + Hibernate create-drop + Flyway disabled |
| 8 | P0/P1 | Fix booking overlap DB protection | DB unique constraint only blocks same start time, not overlapping ranges |
| 9 | P2 | Fix refund enum/schema mismatch | Domain has `PROCESSING`; DB check constraint does not |
| 10 | P2 | Repair HTML docs/index and README docs links | Learning docs exist but navigation and status claims are stale |

---

## 2. Task 1 — Fix Stripe Webhook Idempotency / Transaction Semantics

### Current evidence

Files:

- `src/main/java/com/hien/marketplace/infrastructure/stripe/StripeWebhookHandler.java`
- `src/main/java/com/hien/marketplace/interfaces/rest/StripeWebhookController.java`
- `src/main/java/com/hien/marketplace/infrastructure/persistence/stripe/StripeEventLog.java`

Important lines from current implementation:

- `StripeWebhookHandler.processEvent()` is annotated `@Transactional`.
- It saves `new StripeEventLog(eventId, eventType)` before processing the event.
- It catches `Exception` around the switch and does **not** rethrow.
- `StripeWebhookController` catches all exceptions and returns `400 BAD_REQUEST`.

Current logic shape:

```java
@Transactional
public void processEvent(Event event) {
    eventLogRepository.save(new StripeEventLog(eventId, eventType));

    try {
        switch (eventType) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentPaymentFailed(event);
            default -> log.debug("Unhandled event type: {}", eventType);
        }
    } catch (Exception e) {
        log.error("Error processing event {}", eventId, e);
        // Problem: exception is swallowed, so transaction can still commit.
    }
}
```

### Why this is a bug

The class comment says:

> If domain update fails, event log INSERT rolls back; next webhook delivery will retry.

But because the exception is swallowed:

1. Stripe event arrives.
2. `stripe_event_log` insert succeeds.
3. Payment/order update fails.
4. Exception is logged but not rethrown.
5. Transaction commits.
6. Retry of the same Stripe event is skipped as duplicate.
7. Local payment/order state may remain wrong forever.

This is especially risky for `payment_intent.succeeded` because a real paid Stripe payment may never mark the local order as paid.

### Required fix direction

Use this rule:

> A supported Stripe event must be recorded as processed only after its domain update succeeds.

Minimum acceptable fix:

- Keep `processEvent()` transactional.
- Insert the event log before processing to reserve the event ID.
- If supported event processing fails, rethrow the exception so the transaction rolls back, including the event log insert.
- Duplicate event ID should still be treated as already processed and return success.
- Unsupported event types can be logged and treated as no-op processed, because no domain update is expected.

Pseudo-shape:

```java
@Transactional
public void processEvent(Event event) {
    String eventId = event.getId();
    String eventType = event.getType();

    try {
        eventLogRepository.saveAndFlush(new StripeEventLog(eventId, eventType));
    } catch (DataIntegrityViolationException e) {
        log.info("Stripe event {} already processed, skipping", eventId);
        return;
    }

    switch (eventType) {
        case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
        case "payment_intent.payment_failed" -> handlePaymentIntentPaymentFailed(event);
        default -> log.debug("Unhandled Stripe event type: {}", eventType);
    }
}
```

Important notes:

- `saveAndFlush` can make duplicate-key detection happen immediately, not later at transaction commit.
- Do **not** catch and swallow exceptions from supported event handlers.
- If keeping `ResourceNotFoundException` as ignorable for events from another environment, make that an explicit decision and document it. Safer for this project: let it fail so Stripe retries and Hien sees the issue in logs/tests.

### Controller behavior

Current controller returns `400` for everything:

```java
catch (Exception e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
}
```

Better behavior:

- Invalid Stripe signature / malformed payload: `400 BAD_REQUEST`.
- Internal processing failure after valid signature: `500 INTERNAL_SERVER_ERROR`, so Stripe retries.

Implementation approach:

```java
try {
    Event event = webhookHandler.verifySignature(payload, sigHeader);
    webhookHandler.processEvent(event);
    return ResponseEntity.ok().build();
} catch (StripeApiException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
} catch (Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
}
```

Check existing exception types before coding; if `verifySignature()` wraps invalid signature in `StripeApiException`, catch that specifically.

### Tests required

Add focused tests. Best options:

1. Unit test for handler with mocked `PaymentService` and real/in-memory repository if easy.
2. PostgreSQL/TestContainers test if possible, because transaction semantics matter.

Required scenarios:

| Test | Expected result |
|---|---|
| Duplicate event already in `stripe_event_log` | Handler skips and does not call `PaymentService` again |
| Supported event domain update throws | Transaction rolls back; event is not marked processed |
| Same event retried after failed domain update | Handler attempts processing again |
| Invalid signature | Controller returns `400` |
| Valid signature but processing failure | Controller returns `500` |

### GLM prompt for this task

```text
Task 1: Fix Stripe webhook idempotency/transaction semantics.

Read:
- docs/glm-fix-plan-2026-06-13.md section 2
- StripeWebhookHandler.java
- StripeWebhookController.java
- StripeEventLog.java

Problem:
StripeWebhookHandler saves stripe_event_log, then catches and swallows processing exceptions. This can mark a Stripe event processed even when payment/order domain update failed.

Required behavior:
- Duplicate event ID = skip safely and return success.
- Supported event processing failure = rethrow so transaction rolls back and Stripe can retry.
- Unsupported event type = safe no-op.
- Controller should return 400 only for invalid signature/payload, and 500 for valid webhook processing failure.

Add tests proving failed domain update does not mark the event processed and duplicate successful events are skipped.
Run ./mvnw test and summarize files changed + test result.
```

---

## 3. Task 2 — Fix `@Transactional protected` Self-invocation in Payment/Refund

### Current evidence

Files:

- `src/main/java/com/hien/marketplace/application/service/PaymentService.java`
- `src/main/java/com/hien/marketplace/application/service/RefundService.java`

Current issue in `PaymentService`:

```java
public String createPayment(...) {
    // Stripe API call outside transaction — good.
    PaymentIntent paymentIntent = stripeClient.createPaymentIntent(...);

    // Problem: self-invocation of protected @Transactional method.
    Payment payment = createPaymentWithOrderUpdate(order, paymentIntent.getId(), paymentMethod);
}

@Transactional
protected Payment createPaymentWithOrderUpdate(...) {
    ...
}
```

Current issue in `RefundService` is the same:

```java
public Refund createRefund(...) {
    // Stripe API call outside transaction — good.
    Refund refund = createRefundWithOrderUpdate(...);
}

@Transactional
protected Refund createRefundWithOrderUpdate(...) {
    ...
}
```

### Why this is a bug

Spring's default transaction management is proxy-based. A method call from one method to another method in the same class does not go through the Spring proxy. Therefore the `@Transactional` annotation on the self-invoked method is not applied as intended.

Also, protected methods are not a good transaction boundary for Spring service design.

### Required fix direction

Do **not** put Stripe network calls inside a DB transaction. That part of the architecture is correct.

Instead, move DB mutations to separate Spring beans with public transactional methods.

Recommended classes:

- `PaymentTransactionService`
- `RefundTransactionService`

Example shape:

```java
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public Payment createPaymentWithOrderUpdate(Order order, String paymentIntentId, String paymentMethod) {
        Payment payment = new Payment(order, order.getTotal());
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setPaymentMethod(paymentMethod);
        payment.markAsProcessing();

        payment = paymentRepository.save(payment);
        order.markAsPendingPayment();
        orderRepository.save(order);

        return payment;
    }
}
```

Then `PaymentService` calls the injected bean:

```java
Payment payment = paymentTransactionService.createPaymentWithOrderUpdate(
    order,
    paymentIntent.getId(),
    paymentMethod
);
```

Same pattern for refund.

### Acceptance criteria

- Stripe API calls remain outside transaction.
- DB mutation methods are public and called through another Spring bean.
- Comments accurately describe the real transaction boundary.
- Existing payment/refund tests pass.

### GLM prompt for this task

```text
Task 2: Fix @Transactional self-invocation in PaymentService and RefundService.

Problem:
PaymentService.createPayment() and RefundService.createRefund() call protected @Transactional methods inside the same class. Spring proxy transactions do not apply to this self-invocation, so the comments claiming atomic DB operations are misleading.

Required behavior:
- Keep Stripe API calls outside database transactions.
- Move DB mutation methods into separate Spring beans with public @Transactional methods.
- Payment DB mutation must save Payment and update Order atomically.
- Refund DB mutation must save Refund and update Order atomically when full refund.
- Update comments to match the real transaction boundary.

Run ./mvnw test and summarize changed files + test result.
```

---

## 4. Task 3 — Fix Payment-by-order Authorization Leak

### Current evidence

File:

- `src/main/java/com/hien/marketplace/interfaces/rest/PaymentController.java`
- `src/main/java/com/hien/marketplace/application/service/PaymentService.java`

Current vulnerable controller method:

```java
@GetMapping("/order/{orderId}")
public ResponseEntity<PaymentResponse> getPaymentByOrder(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long orderId
) {
    return paymentService.getPaymentByOrderId(orderId)
            .map(payment -> ResponseEntity.ok(PaymentResponse.from(payment)))
            .orElse(ResponseEntity.notFound().build());
}
```

The method receives `principal` but does not use it.

Current service method:

```java
@Transactional(readOnly = true)
public Optional<Payment> getPaymentByOrderId(Long orderId) {
    return paymentRepository.findByOrderId(orderId);
}
```

### Why this is a bug

Any authenticated user who knows or guesses another `orderId` can fetch that order's payment details.

This contradicts the controller comment:

> Authorization: Only order owner can create/view payment.

### Required fix direction

Add an authorization-aware method:

```java
@Transactional(readOnly = true)
public Optional<Payment> getPaymentByOrderId(Long userId, Long orderId) {
    return paymentRepository.findByOrderId(orderId)
            .map(payment -> {
                if (!payment.getOrder().getCustomer().getId().equals(userId)) {
                    throw new BusinessRuleViolationException(
                        "Payment",
                        "You can only view your own payments"
                    );
                }
                return payment;
            });
}
```

Controller should call:

```java
paymentService.getPaymentByOrderId(principal.userId(), orderId)
```

Alternative: add repository query `findByOrderIdAndOrderCustomerId(...)`. That is also valid and may avoid loading unauthorized payment, but the service-level authorization check is consistent with existing `getPayment(userId, paymentId)`.

### Tests required

Add unit tests in `PaymentServiceTest` or controller tests:

| Scenario | Expected |
|---|---|
| Owner requests payment by order ID | Success |
| Non-owner requests payment by order ID | `BusinessRuleViolationException` or HTTP 422/403 depending existing exception mapping |
| No payment exists for order | `Optional.empty()` / 404 |

### GLM prompt for this task

```text
Task 3: Fix payment-by-order authorization leak.

Problem:
PaymentController.getPaymentByOrder() receives AuthenticationPrincipal but ignores it. It calls PaymentService.getPaymentByOrderId(orderId), so any authenticated user can fetch another user's payment if they know the orderId.

Required behavior:
- Add an authorization-aware service method: getPaymentByOrderId(userId, orderId).
- Verify payment.order.customer.id equals userId before returning.
- Controller must pass principal.userId().
- Add tests for owner success and non-owner rejection.

Run ./mvnw test and summarize changed files + test result.
```

---

## 5. Task 4 — Fix Vendor `userId == vendorId` Assumption

### Current evidence

Files:

- `src/main/java/com/hien/marketplace/interfaces/rest/VendorServiceController.java`
- `src/main/java/com/hien/marketplace/application/service/VendorServiceManagement.java`
- `src/main/java/com/hien/marketplace/infrastructure/persistence/VendorRepository.java`
- `src/main/java/com/hien/marketplace/application/service/BookingService.java`

Current controller comment and behavior:

```java
// TODO: In Phase 3, we'll get vendorId from user via VendorRepository
// For now, assume userId is vendorId (vendors have vendor profile)
Page<ServiceResponse> services = vendorServiceManagement.getVendorServices(
        principal.userId(),
        pageable
);
```

But repository already supports correct lookup:

```java
Optional<Vendor> findByUserId(Long userId);
```

Booking service already uses the correct pattern:

```java
Vendor vendor = vendorRepository.findByUserId(userId)
    .orElseThrow(...);
```

### Why this is a bug

`users.id` and `vendors.id` are different database identities. They may accidentally match in small local tests, but they are not semantically the same.

If `userId != vendorId`, vendor service endpoints can:

- Return no services for the real vendor.
- Create service under wrong/nonexistent vendor ID.
- Fail authorization incorrectly.

### Required fix direction

Change `VendorServiceManagement` public methods to receive authenticated `userId`, then resolve vendor internally:

```java
private Vendor getVendorByUserId(Long userId) {
    return vendorRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessRuleViolationException(
                    "Vendor profile",
                    "Vendor profile not found. Please complete vendor registration."
            ));
}
```

Then:

```java
public Page<ServiceResponse> getVendorServices(Long userId, Pageable pageable) {
    Vendor vendor = getVendorByUserId(userId);
    return serviceRepository.findByVendorId(vendor.getId(), pageable)
            .map(serviceMapper::toResponse);
}
```

Same for create/update/deactivate.

Update controller comments:

- Do not say `vendorId` is extracted from JWT.
- Say `userId` is extracted from JWT and service layer resolves the vendor profile.

### Tests required

Create test data where `User.id` and `Vendor.id` are intentionally different.

Required scenarios:

| Scenario | Expected |
|---|---|
| Authenticated vendor lists own services | Uses vendor profile ID found by user ID |
| Vendor creates service | Service.vendor.id equals resolved vendor ID, not user ID |
| Vendor A updates Vendor B service | Rejected |
| No vendor profile for user | Clear business exception |

### GLM prompt for this task

```text
Task 4: Fix vendor userId vs vendorId assumption.

Problem:
VendorServiceController passes principal.userId() into methods that expect vendorId. Code comments explicitly assume userId == vendorId. This is wrong because users and vendors are separate tables/entities.

Required behavior:
- VendorServiceManagement methods should accept authenticated userId.
- Resolve Vendor via VendorRepository.findByUserId(userId) inside the service layer.
- Use resolved vendor.getId() for repository queries and ownership checks.
- Update comments to explain userId -> vendor profile lookup.
- Add tests where userId and vendorId differ.

Run ./mvnw test and summarize changed files + test result.
```

---

## 6. Task 5 — Enforce Role-based Access Consistently

### Current evidence

File:

- `src/main/java/com/hien/marketplace/config/SecurityConfig.java`
- `src/main/java/com/hien/marketplace/infrastructure/security/JwtAuthenticationFilter.java`

Current comment:

```java
// ROLE-based: Vendor-only endpoints checked in controllers via @PreAuthorize
```

Actual source search result:

- No `@PreAuthorize` annotations in main source.
- No `@EnableMethodSecurity` in security config.

JWT filter does create authorities:

```java
new SimpleGrantedAuthority("ROLE_" + role)
```

So role-based URL security can work.

### Why this is a bug

Vendor-only endpoints currently rely mostly on authentication plus service-layer ownership checks. A CUSTOMER may reach `/api/vendor/**` code paths until the service layer fails. That is not the same as real role-based endpoint protection.

The docs/comment claim method-level security, but implementation does not match.

### Required fix direction

Pick **one** security strategy.

Recommended for this project right now: URL-level role rules in `SecurityConfig`, because it is simple and matches current JWT authorities.

Example:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/api/auth/**",
        "/api/services/**",
        "/api/reviews/service/**",
        "/api/reviews/vendor/**",
        "/api/webhooks/stripe",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/actuator/health"
    ).permitAll()
    .requestMatchers("/api/vendor/**").hasRole("VENDOR")
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/**").authenticated()
    .anyRequest().permitAll()
)
```

If GLM chooses method security instead, it must also:

```java
@EnableMethodSecurity
```

and add `@PreAuthorize("hasRole('VENDOR')")` on vendor controller/service methods. Do not do both unless there is a clear reason.

### Tests required

MockMvc/security tests:

| Scenario | Expected |
|---|---|
| No token calls `/api/vendor/services` | 401/403 depending existing security behavior |
| CUSTOMER token calls `/api/vendor/services` | 403 |
| VENDOR token calls `/api/vendor/services` | Controller reached |
| Public `/api/services` still works without token | 200 |
| Stripe webhook still public | Signature validation handles request, not JWT |

### GLM prompt for this task

```text
Task 5: Enforce role-based access consistently.

Problem:
SecurityConfig comments say vendor-only endpoints are checked via @PreAuthorize, but the source has no @PreAuthorize and no @EnableMethodSecurity. JWT authorities already use ROLE_<role>, so URL-level role guards are straightforward.

Required behavior:
- Add explicit role protection for /api/vendor/** as VENDOR.
- Add /api/admin/** as ADMIN if admin endpoints exist or are planned.
- Keep public endpoints public: auth, services catalog, public reviews, Stripe webhook, Swagger, health.
- Update misleading comments.
- Add tests that CUSTOMER cannot access vendor endpoints and VENDOR can.

Run ./mvnw test and summarize changed files + test result.
```

---

## 7. Task 6 — Wire `ServiceSpecification` into Real API

### Current evidence

Files:

- `src/main/java/com/hien/marketplace/infrastructure/persistence/specification/ServiceSpecification.java`
- `src/main/java/com/hien/marketplace/interfaces/dto/request/ServiceSearchRequest.java`
- `src/main/java/com/hien/marketplace/interfaces/rest/ServiceController.java`
- `src/main/java/com/hien/marketplace/application/service/ServiceCatalogService.java`
- `src/main/java/com/hien/marketplace/infrastructure/persistence/ServiceRepository.java`

`ServiceRepository` already extends `JpaSpecificationExecutor<ServiceEntity>`.

`ServiceSpecification.fromRequest(request)` exists and supports:

- status ACTIVE always
- keyword
- category
- vendor
- city
- min rating
- price range

But `ServiceController` only exposes:

- `GET /api/services`
- `GET /api/services/{id}`
- `GET /api/services/category/{categoryId}`

No endpoint calls `ServiceSpecification.fromRequest()`.

### Required fix direction

Recommended simple endpoint:

```text
GET /api/services/search
```

Query params map into `ServiceSearchRequest`.

Controller shape:

```java
@GetMapping("/search")
public ResponseEntity<Page<ServiceResponse>> searchServices(
        @Valid ServiceSearchRequest request,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
) {
    return ResponseEntity.ok(serviceCatalogService.searchServices(request, pageable));
}
```

Service shape:

```java
@Transactional(readOnly = true)
public Page<ServiceResponse> searchServices(ServiceSearchRequest request, Pageable pageable) {
    return serviceRepository.findAll(ServiceSpecification.fromRequest(request), pageable)
            .map(this::enrichServiceResponse);
}
```

### Important implementation check

`ServiceSpecification.hasKeyword()` currently uses:

```java
root.get("name")
root.get("description")
```

This matches `ServiceEntity.name`, but public DTO calls it `title`. Do not change entity field unless necessary.

### Tests required

Use integration tests or repository/spec tests.

Required scenarios:

| Filter | Expected |
|---|---|
| No filters | returns active services only |
| `keyword` | matches name/description case-insensitively |
| `categoryId` | returns only category |
| `vendorId` | returns only vendor |
| `city` | returns exact lower-case city match |
| `minPrice`/`maxPrice` | filters cents correctly |
| `minRating` | excludes null ratings and returns rating >= threshold |

### GLM prompt for this task

```text
Task 6: Wire ServiceSpecification into a real API endpoint.

Problem:
ServiceSpecification and ServiceSearchRequest exist, and ServiceRepository extends JpaSpecificationExecutor, but ServiceController/ServiceCatalogService never call ServiceSpecification.fromRequest(). Phase search/filter claims are therefore inflated.

Required behavior:
- Add GET /api/services/search with query params mapped to ServiceSearchRequest.
- Add ServiceCatalogService.searchServices(request, pageable).
- Use serviceRepository.findAll(ServiceSpecification.fromRequest(request), pageable).
- Preserve existing GET /api/services behavior.
- Add tests for keyword, category/vendor/city, price range, rating, and active-only filtering.

Run ./mvnw test and summarize changed files + test result.
```

---

## 8. Task 7 — Add PostgreSQL/TestContainers Tests

### Current evidence

Files:

- `src/test/resources/application-test.yml`
- `src/test/java/com/hien/marketplace/integration/BaseIntegrationTest.java`
- `src/test/java/com/hien/marketplace/integration/BaseDataJpaTest.java`
- `pom.xml`

Current test profile uses H2:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
```

TestContainers infrastructure exists:

```java
public abstract class BaseIntegrationTest { ... PostgreSQLContainer ... }
public abstract class BaseDataJpaTest { ... PostgreSQLContainer ... }
```

But source search found no real test extending those base classes.

### Required fix direction

Do **not** convert the entire suite to Docker-based tests immediately. Keep fast H2 tests for normal unit/integration speed. Add a small number of critical PostgreSQL tests.

Recommended tests:

1. `MigrationPostgresTest extends BaseIntegrationTest`
   - Purpose: Spring context starts with PostgreSQL, Flyway enabled, Hibernate validate.
   - This catches broken migrations/schema mismatch.

2. `ServiceSpecificationPostgresTest extends BaseDataJpaTest`
   - Purpose: dynamic filters against real PostgreSQL.

3. `StripeWebhookIdempotencyPostgresTest extends BaseIntegrationTest`
   - Purpose: transaction rollback/idempotency behavior.

4. `BookingOverlapPostgresTest extends BaseDataJpaTest` or `BaseIntegrationTest`
   - Purpose: PostgreSQL exclusion constraint / locking behavior after Task 8.

### Acceptance criteria

- At least one real test extends `BaseIntegrationTest` or `BaseDataJpaTest`.
- Test logs show PostgreSQL TestContainer is used.
- Flyway is enabled in those tests.
- Hibernate uses `ddl-auto=validate`, not create-drop, for those tests.

### GLM prompt for this task

```text
Task 7: Add targeted PostgreSQL/TestContainers tests.

Problem:
The main test profile uses H2, ddl-auto=create-drop, and Flyway disabled. BaseIntegrationTest/BaseDataJpaTest already exist for PostgreSQL TestContainers, but no real tests extend them.

Required behavior:
- Do not migrate all tests to Docker.
- Add targeted PostgreSQL tests for migrations/schema validation, ServiceSpecification filters, webhook idempotency transaction behavior, and booking overlap after the DB constraint fix.
- Tests must extend BaseIntegrationTest or BaseDataJpaTest.
- Tests must use Flyway enabled and Hibernate validate.

Run ./mvnw test and summarize changed files + test result.
```

---

## 9. Task 8 — Fix Booking Overlap DB Protection

### Current evidence

Files:

- `src/main/resources/db/migration/V4__create_bookings.sql`
- `src/main/java/com/hien/marketplace/application/service/BookingService.java`
- `src/main/java/com/hien/marketplace/domain/common/TimeSlot.java`
- `src/main/java/com/hien/marketplace/infrastructure/persistence/BookingRepository.java`

Current DB constraint:

```sql
CONSTRAINT uq_booking_slot UNIQUE (service_id, booking_date, start_time)
```

Current app-level overlap check:

```java
return this.startTime.isBefore(other.endTime)
    && this.endTime.isAfter(other.startTime);
```

### Why this is incomplete

The app-level check catches normal sequential overlap, but concurrent requests can race:

1. Request A checks availability: no booking.
2. Request B checks availability: no booking.
3. A inserts 09:00–10:00.
4. B inserts 09:30–10:30.
5. DB unique constraint does not block B because start_time differs.

Current unique constraint only blocks exact same start time.

### Required fix direction

Use PostgreSQL exclusion constraint. Add a **new migration**; do not edit V4 if treating migrations as already applied.

Recommended migration name:

```text
V9__add_booking_overlap_exclusion_constraint.sql
```

Recommended SQL:

```sql
-- Enable GiST operator support for equality on BIGINT/DATE.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Prevent overlapping non-cancelled bookings for the same service on the same date.
ALTER TABLE bookings
ADD CONSTRAINT ex_booking_no_overlap
EXCLUDE USING gist (
    service_id WITH =,
    booking_date WITH =,
    tsrange(booking_date + start_time, booking_date + end_time, '[)') WITH &&
)
WHERE (status <> 'CANCELLED');
```

Why `[)`:

- Includes start time.
- Excludes end time.
- Allows back-to-back bookings: 09:00–10:00 and 10:00–11:00.

### Important note

This is PostgreSQL-specific. H2 will not validate this correctly. That is why Task 7 is required.

### Tests required

PostgreSQL TestContainers test:

| Scenario | Expected |
|---|---|
| Insert 09:00–10:00 | Success |
| Insert same service/date 09:30–10:30 | Constraint violation |
| Insert same service/date 10:00–11:00 | Success |
| Insert different service same time | Success |
| Insert overlapping booking where existing is CANCELLED | Success if business rule allows cancelled slots to be reused |

### GLM prompt for this task

```text
Task 8: Add database-level booking overlap protection.

Problem:
V4 has UNIQUE(service_id, booking_date, start_time), which only blocks identical start times. It does not block overlapping ranges like 09:00-10:00 and 09:30-10:30.

Required behavior:
- Add a new Flyway migration using PostgreSQL exclusion constraint to block overlapping non-cancelled bookings for same service/date.
- Use [) range semantics so back-to-back bookings are allowed.
- Keep app-level TimeSlot overlap check as user-friendly early validation.
- Add PostgreSQL/TestContainers tests proving overlap fails and back-to-back succeeds.

Run ./mvnw test and summarize changed files + test result.
```

---

## 10. Task 9 — Fix RefundStatus DB Constraint Mismatch

### Current evidence

Files:

- `src/main/java/com/hien/marketplace/domain/payment/RefundStatus.java`
- `src/main/java/com/hien/marketplace/domain/payment/Refund.java`
- `src/main/resources/db/migration/V5__create_orders_and_payments.sql`

Domain enum:

```java
public enum RefundStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED;
}
```

Migration check constraint:

```sql
CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
```

### Why this matters

Current `RefundService` usually goes directly from `PENDING` to `SUCCEEDED`, so the mismatch may not fail today. But `Refund.markAsProcessing()` exists. If future code persists PROCESSING, PostgreSQL rejects it.

H2/create-drop tests may not catch the production migration mismatch.

### Required fix direction

Add a new migration to update the refunds status constraint to include `PROCESSING`.

Because the original constraint was unnamed, PostgreSQL generated a name. GLM should inspect or use a robust migration block. Example approach:

```sql
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'refunds'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%status%PENDING%SUCCEEDED%FAILED%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE refunds DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE refunds
ADD CONSTRAINT chk_refunds_status
CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'));
```

### Tests required

PostgreSQL migration/schema test should catch this. Optional direct repository test:

- Create refund.
- `markAsProcessing()`.
- Persist/flush.
- Assert no DB constraint failure.

### GLM prompt for this task

```text
Task 9: Fix RefundStatus DB constraint mismatch.

Problem:
RefundStatus enum includes PROCESSING and Refund.markAsProcessing() can set it, but V5 refunds.status check constraint only allows PENDING, SUCCEEDED, FAILED.

Required behavior:
- Add a new Flyway migration to replace/update the refunds status check constraint to include PROCESSING.
- Prefer an explicit new constraint name.
- Add PostgreSQL-backed test or migration validation proving PROCESSING can persist.

Run ./mvnw test and summarize changed files + test result.
```

---

## 11. Task 10 — Repair HTML Docs Index / Language Links / README Links

### Current evidence

Files:

- `docs/html/index.html`
- `docs/html/en/roadmap.html`
- `docs/html/vi/phase3/**`
- `docs/html/en/phase3/**`
- `docs/html/vi/phase4/**`
- `docs/html/en/phase4/**`
- `README.md`

Previous link checker result:

```text
HTML files: 67
Internal links checked: 600
Broken internal links: 27
```

Known issues:

- `docs/html/index.html` still shows Phase 4 as `0/4` even though Phase 4 docs exist.
- Phase 3/4 language switchers use wrong relative paths in several files.
- `docs/html/en/roadmap.html` has broken relative links to `vi/...` paths.
- README references docs folders like API/C4/state/sequence docs that may exist as directories but have no useful files yet.

### Required fix direction

Do this after code fixes, not before.

Fixes:

1. Update `docs/html/index.html` Phase 4 counts and links.
2. Fix language switch links:
   - From phase docs, use `../../en/...` or `../../vi/...` as needed, not `../en/...` from the wrong directory level.
3. Re-run HTML link checker and target zero broken internal links.
4. README should not imply complete docs if directories are empty. Options:
   - Add placeholder docs with honest “planned” status.
   - Or update README to mark these docs as pending.

### GLM prompt for this task

```text
Task 10: Repair HTML docs navigation and README docs links.

Problem:
The audit found 27 broken internal HTML links. docs/html/index.html still shows Phase 4 as 0/4 despite Phase 4 docs existing. Some language switcher links in Phase 3/4 use the wrong relative path. README links to architecture/API docs that are incomplete or empty.

Required behavior:
- Update docs/html/index.html to reflect actual Phase 4 docs.
- Fix Phase 3/4 VI/EN language switch links.
- Fix docs/html/en/roadmap.html relative links.
- Remove noise files like .backup/.DS_Store only if they are not needed.
- Update README docs section honestly: either link real files or mark missing docs as planned.
- Re-run link checker and report result.

Run ./mvnw test only if production docs build/test depends on it; otherwise run the HTML link checker and summarize changed files.
```

---

## 12. Suggested Commit Plan

Use small commits so review is easy.

```text
fix: Make Stripe webhook idempotency transactional
fix: Move payment refund DB mutations to transactional services
fix: Authorize payment lookup by order owner
fix: Resolve vendor services through vendor profile
fix: Enforce vendor role access
feat: Add service search endpoint using specifications
test: Add PostgreSQL integration coverage
fix: Prevent overlapping bookings in PostgreSQL
fix: Align refund status schema with domain enum
docs: Repair audit follow-up docs and navigation
```

If one PR becomes too large, split into two PRs:

### PR 1 — correctness/security

Branch:

```text
fix/phase4-correctness-security
```

Include:

- webhook idempotency
- transactional services
- payment authorization
- vendor lookup
- role security

### PR 2 — search/database/docs polish

Branch:

```text
fix/phase4-search-db-docs
```

Include:

- ServiceSpecification API
- TestContainers tests
- booking overlap constraint
- refund schema mismatch
- HTML/README docs

Recommended for GLM: **one PR is okay only if each commit is small and tests stay green after each commit**.

---

## 13. Final All-in-one Prompt for GLM

Use this if Hien wants to send one complete instruction to GLM.

```text
Read docs/glm-fix-plan-2026-06-13.md fully before coding.

Goal:
Fix the audit findings from Phase 0–4 in small, reviewable commits. Do not do speculative refactors. Keep changes surgical and test-backed.

Branch:
Create fix/phase4-audit-correctness.

Priority order:
1. Fix Stripe webhook idempotency/transaction semantics.
2. Fix PaymentService/RefundService @Transactional self-invocation by moving DB mutations to separate public @Transactional beans. Keep Stripe network calls outside DB transactions.
3. Fix PaymentController.getPaymentByOrder() authorization by checking order ownership.
4. Fix VendorServiceController/VendorServiceManagement userId vs vendorId assumption by resolving Vendor via VendorRepository.findByUserId(userId).
5. Enforce vendor/admin role access consistently in SecurityConfig or method security. Prefer URL-level rules unless there is a strong reason otherwise.
6. Wire ServiceSpecification into a real GET /api/services/search endpoint and add tests.
7. Add targeted PostgreSQL/TestContainers tests using existing BaseIntegrationTest/BaseDataJpaTest for migrations/schema, webhook idempotency, service specification, booking overlap.
8. Add PostgreSQL database-level booking overlap protection with exclusion constraint and tests.
9. Fix RefundStatus PROCESSING mismatch with DB check constraint.
10. Repair docs/html index/language links and README docs links after code fixes.

Rules:
- One focused change at a time.
- Add or update tests for every behavior change.
- Run ./mvnw test after meaningful changes.
- Do not hide failing tests. If a test fails, stop and report exact output.
- Do not rewrite unrelated code or formatting.
- Update comments when current comments are no longer true.

Final output required:
- Files changed grouped by task.
- Tests run and exact result.
- Any skipped/deferred item with reason.
- Suggested PR title/body.
```

---

## 14. Review Checklist for Main Model After GLM Finishes

When GLM returns a diff, review with this checklist:

| Check | Must be true |
|---|---|
| Webhook failure semantics | Supported event failure does not mark event processed |
| Duplicate webhook semantics | Duplicate successful event is skipped safely |
| Controller response | Invalid signature is 400; valid processing failure is not hidden as success |
| Transaction boundary | Stripe calls outside DB transaction; DB mutations inside real proxied transaction |
| Payment auth | Payment by order ID checks owner |
| Vendor lookup | Vendor endpoints resolve vendor profile by userId |
| Role access | CUSTOMER cannot access vendor endpoints |
| Search API | `ServiceSpecification.fromRequest()` is actually called |
| PostgreSQL tests | At least some tests extend TestContainers base classes |
| Booking overlap | DB blocks overlapping ranges, not just equal start time |
| Refund status | DB allows all enum values that domain can persist |
| Docs | Link checker has zero broken internal links or remaining breaks are listed honestly |

---

## 15. What Not To Do

- Do not start Phase 5 Redis before these P0/P1 fixes.
- Do not only update docs/evaluation scores while code remains wrong.
- Do not put Stripe API calls inside a DB transaction.
- Do not use `userId` as `vendorId` anywhere.
- Do not claim TestContainers coverage unless tests actually extend the TestContainers base classes.
- Do not mark webhook events processed before successful supported event handling.
- Do not silently swallow payment webhook domain update failures.
