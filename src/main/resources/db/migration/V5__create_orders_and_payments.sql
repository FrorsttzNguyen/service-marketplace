-- =============================================================================
-- V5: Orders, Payments, Refunds & Stripe Event Log
-- Order = aggregate root. Payment/Refund accessed only through Order.
-- stripe_event_log ensures webhook idempotency (unique event_id).
-- =============================================================================

CREATE TABLE orders (
    id                  BIGSERIAL       PRIMARY KEY,
    customer_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    booking_id          BIGINT          NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    status              VARCHAR(20)     NOT NULL DEFAULT 'CREATED'
                        CHECK (status IN ('CREATED', 'PENDING_PAYMENT', 'PAID', 'FULFILLED', 'CANCELLED', 'REFUNDED')),
    subtotal_cents      BIGINT          NOT NULL,
    commission_cents    BIGINT          NOT NULL DEFAULT 0,
    total_cents         BIGINT          NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index: customer order history
CREATE INDEX idx_orders_customer_id ON orders(customer_id);

-- Index: find order by booking
CREATE INDEX idx_orders_booking_id ON orders(booking_id);

CREATE TABLE payments (
    id                          BIGSERIAL       PRIMARY KEY,
    order_id                    BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    stripe_payment_intent_id    VARCHAR(255)    UNIQUE,
    amount_cents                BIGINT          NOT NULL,
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    payment_method              VARCHAR(50),
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index: webhook lookup - find payment by Stripe ID (runs every webhook)
CREATE INDEX idx_payments_stripe_id ON payments(stripe_payment_intent_id);

-- Index: find payments by order
CREATE INDEX idx_payments_order_id ON payments(order_id);

CREATE TABLE refunds (
    id                  BIGSERIAL       PRIMARY KEY,
    payment_id          BIGINT          NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    amount_cents        BIGINT          NOT NULL,
    reason              TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    stripe_refund_id    VARCHAR(255),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);

-- Idempotency table: ensures every Stripe webhook event is processed exactly once
CREATE TABLE stripe_event_log (
    stripe_event_id     VARCHAR(255)    PRIMARY KEY,
    event_type          VARCHAR(100)    NOT NULL,
    processed_at        TIMESTAMP       NOT NULL DEFAULT NOW()
);
