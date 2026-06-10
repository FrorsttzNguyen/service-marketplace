# ADR 0004: Stripe Webhook Idempotency Approach

## Status
Accepted

## Context
Stripe may deliver the same webhook event multiple times (at-least-once delivery). If we process a `payment_intent.succeeded` event twice, we could:
- Create duplicate Payment records
- Send duplicate notifications
- Update booking status twice (potentially incorrect transition)

## Decision
Use **idempotency key based on Stripe event ID** with a dedicated processing log table.

```sql
CREATE TABLE stripe_event_log (
    id BIGSERIAL PRIMARY KEY,
    stripe_event_id VARCHAR(255) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

Processing flow:
```
1. Receive webhook
2. Verify Stripe signature
3. BEGIN TRANSACTION
4. INSERT INTO stripe_event_log (stripe_event_id) — if duplicate key, ROLLBACK and return 200
5. Process event (update payment, order, booking)
6. COMMIT
```

## Consequences

### Positive
- **Exactly-once processing** — The UNIQUE constraint on `stripe_event_id` guarantees no duplicate processing.
- **Simple implementation** — Single table, single constraint. No distributed locks needed.
- **Auditable** — Every processed webhook is logged with timestamp.
- **Stripe recommends this pattern** — Shows awareness of best practices.

### Negative
- **Database round-trip** — Every webhook checks the log table. Negligible at this scale.
- **Table growth** — Event log grows indefinitely. Mitigate with periodic archival (cron job) or TTL policy.
- **Single-database scope** — If we extracted payment into a microservice, this still works (the log lives with the payment data).

### Alternative Considered
Stripe recommends returning 200 OK for already-processed events. Our approach returns 200 in all cases (whether newly processed or already seen), which is correct.
