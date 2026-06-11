# ADR 0004: Stripe Webhook Idempotency Approach

## Status
Accepted

## Context
Stripe may deliver the same webhook event multiple times (at-least-once delivery). If we process a `payment_intent.succeeded` event twice, we could:

- Create duplicate local side effects
- Send duplicate notifications
- Update order/booking status twice
- Write duplicate ledger/audit records once those features exist

A duplicate webhook does not normally charge the customer twice by itself; Stripe charges through the PaymentIntent. The risk is duplicate processing inside our system.

## Decision
Use **idempotency based on Stripe event ID** with a dedicated processing log table.

Current Phase 1 schema:

```sql
CREATE TABLE stripe_event_log (
    stripe_event_id VARCHAR(255) PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

Processing flow for the future webhook handler:

```text
1. Receive webhook
2. Verify Stripe signature
3. BEGIN TRANSACTION
4. INSERT INTO stripe_event_log (stripe_event_id, event_type)
   - if duplicate key, ROLLBACK and return 200
5. Process event (update payment, order, booking/notification as needed)
6. COMMIT
```

`payments.stripe_payment_intent_id` has a separate purpose: it prevents duplicate local payment records for the same Stripe PaymentIntent. It is not a replacement for `stripe_event_log`, because Stripe may send many event IDs for the same PaymentIntent lifecycle.

## Consequences

### Positive
- **Idempotent processing** — The primary key on `stripe_event_id` prevents processing the same webhook event twice.
- **Simple implementation** — Single table, single constraint. No distributed locks needed.
- **Auditable** — Every processed webhook is logged with timestamp.
- **Stripe-compatible pattern** — Returning `200 OK` for already-processed events is correct.

### Negative
- **Database round-trip** — Every webhook checks/inserts into the log table. Negligible at this scale.
- **Table growth** — Event log grows indefinitely. Mitigate with periodic archival or retention policy.
- **Phase gap** — Phase 1 only has schema support; the actual Stripe SDK integration and webhook controller are implemented later.

### Alternative Considered
Using only `stripe_payment_intent_id` on `payments` was rejected because webhook idempotency is event-based. A PaymentIntent can produce multiple distinct events, and each event should be tracked independently.
