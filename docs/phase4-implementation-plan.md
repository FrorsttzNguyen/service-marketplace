# Phase 4: Payment Integration - Implementation Plan

**Date:** 2026-06-12
**Status:** READY FOR IMPLEMENTATION
**Goal:** Real payment flow with Stripe integration

---

## Executive Summary

Phase 4 implements **Stripe payment integration** with:
- PaymentIntent creation for checkout
- Webhook handling with idempotency
- Refund flow (full + partial)
- Vendor commission tracking

**Foundation Status:**
- ✅ Domain models exist: Payment, Order, Refund (Phase 1)
- ✅ Database schema ready: orders, payments, refunds, stripe_event_log (V5 migration)
- ✅ ADR defined: Stripe webhook idempotency approach (ADR 0004)
- ✅ API patterns established: Controllers, DTOs, validation, exception handling (Phase 2)
- ✅ Business patterns ready: State machine, optimistic locking, domain events (Phase 3)

---

## Phase Review Summary (From 4 Agents)

| Phase | Score | Status | Key Findings |
|-------|-------|--------|--------------|
| **Phase 0** | 8.25/10 | EXCELLENT | Strong foundation, missing sealed classes |
| **Phase 1** | 9.5/10 | EXCELLENT | Domain model is textbook-perfect DDD |
| **Phase 2** | 9.0/10 | STRONG | JWT auth excellent, API patterns solid |
| **Phase 3** | 8.75/10 | GOOD | @TransactionalEventListener fix applied |

### Critical Patterns to Apply in Phase 4

From Phase reviews, these patterns MUST continue:

| Pattern | Source | Phase 4 Application |
|---------|--------|---------------------|
| **State Machine** | BookingStatus (Phase 1) | PaymentStatus with valid transitions |
| **Optimistic Locking** | Booking @Version (Phase 3) | Add @Version to Payment entity |
| **Domain Events** | BookingConfirmedEvent (Phase 3) | PaymentSucceededEvent, RefundProcessedEvent |
| **@TransactionalEventListener** | Listeners (Phase 3) | Payment webhook listeners |
| **Value Objects** | Money (Phase 1) | Use Money for all payment amounts |
| **DTO Pattern** | All controllers (Phase 2) | PaymentRequest, PaymentResponse DTOs |

---

## Architecture Design

### Package Structure

```
com.hien.marketplace
├── domain/payment/
│   ├── Payment.java              [EXISTS - ENHANCE]
│   ├── PaymentStatus.java        [EXISTS - ENHANCE with state machine]
│   ├── Refund.java               [EXISTS - ENHANCE]
│   ├── RefundStatus.java         [EXISTS]
│   └── events/                   [NEW]
│       ├── PaymentSucceededEvent.java
│       ├── PaymentFailedEvent.java
│       └── RefundProcessedEvent.java
│
├── domain/order/
│   ├── Order.java                [EXISTS - ENHANCE]
│   └── OrderStatus.java          [EXISTS - ENHANCE with state machine]
│
├── application/service/
│   ├── PaymentService.java       [NEW]
│   └── RefundService.java        [NEW]
│
├── application/listener/
│   └── PaymentEventListener.java [NEW]
│
├── interfaces/payment/
│   ├── PaymentController.java    [NEW]
│   ├── RefundController.java     [NEW]
│   └── StripeWebhookController.java [NEW - PUBLIC endpoint]
│
├── interfaces/payment/dto/
│   ├── PaymentCreateRequest.java [NEW]
│   ├── PaymentResponse.java      [NEW]
│   ├── RefundRequest.java        [NEW]
│   └── RefundResponse.java       [NEW]
│
├── infrastructure/stripe/        [NEW]
│   ├── StripeClient.java
│   ├── StripeConfig.java
│   └── StripeWebhookHandler.java
│
└── infrastructure/persistence/
    └── StripeEventLogRepository.java [NEW]
```

---

## Implementation Phases

### Phase 4.1: Domain Enhancement (2-3 days)

#### 4.1.1 PaymentStatus State Machine

**Problem:** Current PaymentStatus is simple enum without transition validation.

**Solution:** Add state machine pattern like BookingStatus:

```java
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED;

    // State machine: valid transitions
    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
        PENDING, Set.of(PROCESSING),
        PROCESSING, Set.of(SUCCEEDED, FAILED),
        SUCCEEDED, Set.of(),  // Terminal - no outgoing transitions
        FAILED, Set.of(PENDING)  // Allow retry
    );

    public boolean canTransitionTo(PaymentStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void throwIfInvalidTransition(PaymentStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                "Cannot transition from " + this + " to " + target
            );
        }
    }
}
```

#### 4.1.2 OrderStatus State Machine

Same pattern for OrderStatus:

```java
public enum OrderStatus {
    CREATED,
    PENDING_PAYMENT,
    PAID,
    FULFILLED,
    CANCELLED,
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
        CREATED, Set.of(PENDING_PAYMENT, CANCELLED),
        PENDING_PAYMENT, Set.of(PAID, CANCELLED),
        PAID, Set.of(FULFILLED, REFUNDED),
        FULFILLED, Set.of(),  // Terminal
        CANCELLED, Set.of(),  // Terminal
        REFUNDED, Set.of()    // Terminal
    );
}
```

#### 4.1.3 Add @Version to Payment

**Why:** Concurrent webhook events could update same payment.

```java
@Entity
@Table(name = "payments")
public class Payment {
    // ... existing fields

    @Version
    private Long version;  // NEW - optimistic locking

    // Domain methods with transition validation
    public void markAsProcessing() {
        status.throwIfInvalidTransition(PaymentStatus.PROCESSING);
        this.status = PaymentStatus.PROCESSING;
    }

    public void markAsSucceeded() {
        status.throwIfInvalidTransition(PaymentStatus.SUCCEEDED);
        this.status = PaymentStatus.SUCCEEDED;
    }

    public void markAsFailed() {
        status.throwIfInvalidTransition(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
    }
}
```

#### 4.1.4 Domain Events

Create events in `domain/payment/events/`:

```java
public record PaymentSucceededEvent(
    Long paymentId,
    Long orderId,
    Long customerId,
    String stripePaymentIntentId,
    LocalDateTime occurredAt
) {}

public record PaymentFailedEvent(
    Long paymentId,
    Long orderId,
    String failureReason,
    LocalDateTime occurredAt
) {}

public record RefundProcessedEvent(
    Long refundId,
    Long paymentId,
    Long orderId,
    Money amount,
    LocalDateTime occurredAt
) {}
```

---

### Phase 4.2: Stripe Infrastructure (2-3 days)

#### 4.2.1 StripeConfig

```java
@Configuration
@ConfigurationProperties(prefix = "stripe")
@Validated
public class StripeConfig {
    @NotBlank
    private String apiKey;

    @NotBlank
    private String webhookSecret;

    // Getters + Setters

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
    }
}
```

**application.yml:**
```yaml
stripe:
  api-key: ${STRIPE_API_KEY:sk_test_...}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET:whsec_...}
```

#### 4.2.2 StripeClient

Wrapper for Stripe API calls - **CRITICAL: All calls outside @Transactional**:

```java
@Component
@RequiredArgsConstructor
public class StripeClient {
    private final StripeConfig config;

    /**
     * Create PaymentIntent - OUTSIDE transaction
     * WHY: Stripe API call should not hold DB transaction open
     */
    public PaymentIntent createPaymentIntent(Money amount, Long orderId) throws StripeException {
        Map<String, Object> params = Map.of(
            "amount", amount.getAmountCents(),
            "currency", "usd",
            "metadata", Map.of("order_id", orderId.toString())
        );
        return PaymentIntent.create(params);
    }

    /**
     * Retrieve PaymentIntent by ID
     */
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }

    /**
     * Create refund - OUTSIDE transaction
     */
    public com.stripe.model.Refund createRefund(String paymentIntentId, Long amountCents)
            throws StripeException {
        Map<String, Object> params = new HashMap<>();
        params.put("payment_intent", paymentIntentId);
        if (amountCents != null) {
            params.put("amount", amountCents);  // Partial refund
        }
        return com.stripe.model.Refund.create(params);
    }
}
```

#### 4.2.3 StripeWebhookHandler

```java
@Component
@RequiredArgsConstructor
public class StripeWebhookHandler {
    private final StripeConfig config;
    private final PaymentService paymentService;
    private final StripeEventLogRepository eventLogRepository;

    /**
     * Verify Stripe signature - CRITICAL for security
     */
    public Event verifySignature(String payload, String sigHeader) {
        try {
            return Webhook.constructEvent(payload, sigHeader, config.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new StripeWebhookException("Invalid signature", e);
        }
    }

    /**
     * Process webhook event with idempotency
     */
    @Transactional
    public void processEvent(Event event) {
        String eventId = event.getId();

        // IDEMPOTENCY: Check if already processed
        if (eventLogRepository.existsById(eventId)) {
            log.info("Event {} already processed, skipping", eventId);
            return;
        }

        // Log event BEFORE processing (fails fast if duplicate)
        eventLogRepository.save(new StripeEventLog(eventId, event.getType()));

        // Process based on event type
        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentFailed(event);
            // Add more event types as needed
        }
    }

    private void handlePaymentSucceeded(Event event) {
        // Extract PaymentIntent from event
        // Find local Payment by stripePaymentIntentId
        // Update status to SUCCEEDED
        // Publish PaymentSucceededEvent
    }

    private void handlePaymentFailed(Event event) {
        // Similar flow
    }
}
```

---

### Phase 4.3: Application Services (2-3 days)

#### 4.3.1 PaymentService

```java
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final StripeClient stripeClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Create payment for an order
     *
     * FLOW:
     * 1. Validate order ownership & status
     * 2. Create Stripe PaymentIntent (OUTSIDE transaction)
     * 3. Create local Payment with PaymentIntent ID (INSIDE transaction)
     * 4. Return client secret for frontend
     */
    public PaymentResponse createPayment(Long userId, PaymentCreateRequest request) {
        // Step 1: Validate order (transactional read)
        Order order = orderRepository.findById(request.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));

        if (!order.getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Payment", "You can only pay for your own orders");
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessRuleViolationException("Order status", "Order is not eligible for payment");
        }

        // Check if payment already exists
        if (paymentRepository.existsByOrderId(request.orderId())) {
            throw new DuplicateResourceException("Payment", "orderId", request.orderId());
        }

        // Step 2: Create Stripe PaymentIntent (OUTSIDE transaction)
        PaymentIntent intent;
        try {
            intent = stripeClient.createPaymentIntent(order.getTotal(), order.getId());
        } catch (StripeException e) {
            throw new StripeApiException("Failed to create payment intent", e);
        }

        // Step 3: Create local Payment (INSIDE transaction)
        Payment payment = new Payment(order, order.getTotal());
        payment.setStripePaymentIntentId(intent.getId());
        payment.setPaymentMethod(request.paymentMethod());
        payment.markAsProcessing();
        payment = paymentRepository.save(payment);

        // Update order status
        order.markAsPendingPayment();

        return new PaymentResponse(
            payment.getId(),
            intent.getId(),
            intent.getClientSecret(),  // For Stripe.js
            payment.getStatus(),
            order.getTotal(),
            payment.getCreatedAt()
        );
    }

    /**
     * Handle successful payment from webhook
     */
    @Transactional
    public void handlePaymentSucceeded(String paymentIntentId) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentIntentId));

        payment.markAsSucceeded();
        payment.getOrder().markAsPaid();

        // Publish event for notifications
        eventPublisher.publishEvent(new PaymentSucceededEvent(
            payment.getId(),
            payment.getOrder().getId(),
            payment.getOrder().getCustomer().getId(),
            paymentIntentId,
            LocalDateTime.now()
        ));
    }
}
```

#### 4.3.2 RefundService

```java
@Service
@RequiredArgsConstructor
public class RefundService {
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final StripeClient stripeClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Create refund - full or partial
     */
    public RefundResponse createRefund(Long userId, Long paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        // Authorization: only order owner or ADMIN can refund
        if (!payment.getOrder().getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Refund", "You can only refund your own payments");
        }

        // Business rule: only succeeded payments can be refunded
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new BusinessRuleViolationException("Payment status", "Only succeeded payments can be refunded");
        }

        // Validate refund amount
        Money refundAmount = request.amount() != null
            ? request.amount()
            : payment.getAmount();  // Full refund

        if (refundAmount.getAmountCents() > payment.getAmount().getAmountCents()) {
            throw new BusinessRuleViolationException("Refund amount", "Refund cannot exceed payment amount");
        }

        // Check total refunds don't exceed payment
        Money alreadyRefunded = calculateTotalRefunded(payment);
        if (alreadyRefunded.add(refundAmount).getAmountCents() > payment.getAmount().getAmountCents()) {
            throw new BusinessRuleViolationException("Refund amount", "Total refunds cannot exceed payment");
        }

        // Create Stripe refund (OUTSIDE transaction)
        com.stripe.model.Refund stripeRefund;
        try {
            stripeRefund = stripeClient.createRefund(
                payment.getStripePaymentIntentId(),
                refundAmount.getAmountCents()
            );
        } catch (StripeException e) {
            throw new StripeApiException("Failed to create refund", e);
        }

        // Create local Refund (INSIDE transaction)
        Refund refund = new Refund(payment, refundAmount, request.reason());
        refund.setStripeRefundId(stripeRefund.getId());
        refund.markAsSucceeded();
        refund = refundRepository.save(refund);

        // Update order status if full refund
        if (refundAmount.equals(payment.getAmount())) {
            payment.getOrder().refund();
        }

        // Publish event
        eventPublisher.publishEvent(new RefundProcessedEvent(
            refund.getId(),
            payment.getId(),
            payment.getOrder().getId(),
            refundAmount,
            LocalDateTime.now()
        ));

        return new RefundResponse(refund);
    }

    private Money calculateTotalRefunded(Payment payment) {
        return payment.getRefunds().stream()
            .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED)
            .map(Refund::getAmount)
            .reduce(Money.of(0), Money::add);
    }
}
```

---

### Phase 4.4: Controllers (1-2 days)

#### 4.4.1 PaymentController

Follow existing controller patterns from Phase 2:

```java
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create payment",
               description = "Initiate Stripe payment for an order")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Payment created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "409", description = "Payment already exists")
    })
    public ResponseEntity<PaymentResponse> createPayment(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody PaymentCreateRequest request
    ) {
        PaymentResponse response = paymentService.createPayment(
            principal.userId(), request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment details")
    public ResponseEntity<PaymentResponse> getPayment(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        // Implementation
    }
}
```

#### 4.4.2 StripeWebhookController - PUBLIC endpoint

```java
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks")
public class StripeWebhookController {

    private final StripeWebhookHandler webhookHandler;

    /**
     * CRITICAL: This endpoint is PUBLIC (no JWT)
     * Security comes from Stripe signature verification
     */
    @PostMapping("/stripe")
    @Operation(summary = "Stripe webhook",
               description = "Handle Stripe payment events")
    public ResponseEntity<Void> handleStripeWebhook(
        @RequestBody String payload,
        @Header("Stripe-Signature") String sigHeader
    ) {
        try {
            // 1. Verify signature
            Event event = webhookHandler.verifySignature(payload, sigHeader);

            // 2. Process event with idempotency
            webhookHandler.processEvent(event);

            return ResponseEntity.ok().build();
        } catch (StripeWebhookException e) {
            log.error("Webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
```

**SecurityConfig update:**
```java
.requestMatchers("/api/webhooks/stripe").permitAll()  // PUBLIC for webhooks
```

---

### Phase 4.5: Tests (2 days)

#### 4.5.1 Unit Tests

- `PaymentStatusTest` - State machine transitions
- `OrderStatusTest` - State machine transitions
- `PaymentTest` - Entity behavior with transitions
- `PaymentServiceTest` - Service logic with mocked Stripe

#### 4.5.2 Integration Tests

- `PaymentControllerIntegrationTest` - Full flow with MockMvc
- `StripeWebhookControllerIntegrationTest` - Webhook handling

#### 4.5.3 Stripe Mocking

Use Stripe's test mode and mock API:

```java
@TestConfiguration
public class StripeTestConfig {
    @Bean
    @Primary
    public StripeClient stripeClient() {
        // Return mock that doesn't call real Stripe
        return mock(StripeClient.class);
    }
}
```

---

### Phase 4.6: Learning Docs (2-3 days)

**Location:** `docs/html/vi/phase4/` and `docs/html/en/phase4/`

#### Required Documents (4 docs)

| # | Document | Topics |
|---|----------|--------|
| 01 | `stripe-integration.html` | Stripe API, PaymentIntent, test mode, client secret |
| 02 | `webhook-handling.html` | Signature verification, idempotency, event types |
| 03 | `refund-flow.html` | Full/partial refunds, refund policies, Stripe Refund API |
| 04 | `payment-security.html` | PCI compliance, webhook security, no card data storage |

**Format Requirements:**
- HTML with shared `styles.css` (copy from phase3)
- Navigation links to all 4 docs
- Language switcher (VI/EN)
- Diagrams: Payment flow sequence, Webhook processing flow
- Code snippets with syntax highlighting
- "Tại sao" sections explaining WHY decisions

---

## Database Changes

### Migration V7__add_payment_version.sql

```sql
-- Add optimistic locking to payments table
ALTER TABLE payments ADD COLUMN version BIGINT DEFAULT 0;

-- Add index for webhook lookup by status
CREATE INDEX idx_payments_status ON payments(status);
```

---

## Configuration

### application.yml additions

```yaml
# Stripe configuration
stripe:
  api-key: ${STRIPE_API_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}

# Environment variables needed:
# STRIPE_API_KEY=sk_test_xxx (test mode)
# STRIPE_WEBHOOK_SECRET=whsec_xxx (from Stripe CLI or dashboard)
```

### .env.example additions

```env
# Stripe (Phase 4)
STRIPE_API_KEY=sk_test_your_test_key_here
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret_here
```

---

## Timeline Estimate

| Phase | Task | Duration |
|-------|------|----------|
| 4.1 | Domain Enhancement | 2-3 days |
| 4.2 | Stripe Infrastructure | 2-3 days |
| 4.3 | Application Services | 2-3 days |
| 4.4 | Controllers | 1-2 days |
| 4.5 | Tests | 2 days |
| 4.6 | Learning Docs | 2-3 days |
| **Total** | | **11-16 days** |

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Stripe API changes | Medium | Use stable API version, mock in tests |
| Webhook delivery delays | Low | Use idempotency, return 200 quickly |
| Concurrent webhook processing | Medium | @Version on Payment, idempotency log |
| Test mode vs production | High | Document differences, use env variables |

---

## Success Criteria

### Phase 4 Verification Checklist

- [ ] All existing tests pass
- [ ] New unit tests: PaymentStatus, OrderStatus state machines
- [ ] New integration tests: PaymentController, WebhookController
- [ ] Stripe test mode payment completes successfully
- [ ] Webhook correctly updates payment/order status
- [ ] Duplicate webhook delivery doesn't double-process
- [ ] Refund creates correct Refund entity and updates balance
- [ ] Learning docs: 4 docs (VI + EN) with diagrams
- [ ] ADR updated: Stripe integration patterns
- [ ] All code has WHY comments

---

## Next Steps

1. **Review this plan with Hien** before implementation
2. Create branch: `feat/phase4-payment-integration`
3. Start with Phase 4.1 (Domain Enhancement)
4. Write learning docs in parallel with implementation
5. Run tests continuously
6. Create PR when Phase 4 complete

---

**Implementation Plan Status: READY FOR REVIEW**
