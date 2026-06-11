-- =============================================================================
-- V6: Reviews, Notifications & Audit Logs
-- Reviews: one per booking, rating 1-5.
-- Notifications: per-user, typed.
-- Audit Logs: JSONB for flexible old/new value tracking.
-- =============================================================================

CREATE TABLE reviews (
    id              BIGSERIAL       PRIMARY KEY,
    booking_id      BIGINT          NOT NULL REFERENCES bookings(id) ON DELETE CASCADE UNIQUE,
    customer_id     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vendor_id       BIGINT          NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    service_id      BIGINT          NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    rating          INTEGER         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index: list reviews for a vendor (vendor profile page)
CREATE INDEX idx_reviews_vendor_id ON reviews(vendor_id);

-- Index: list reviews for a service
CREATE INDEX idx_reviews_service_id ON reviews(service_id);

CREATE TABLE notifications (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(50)     NOT NULL
                    CHECK (type IN (
                        'BOOKING_CONFIRMED', 'BOOKING_CANCELLED',
                        'PAYMENT_RECEIVED', 'PAYMENT_FAILED',
                        'REVIEW_RECEIVED', 'VENDOR_APPROVED',
                        'VENDOR_REJECTED'
                    )),
    title           VARCHAR(255)    NOT NULL,
    message         TEXT,
    is_read         BOOLEAN         NOT NULL DEFAULT false,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index: fetch unread notifications for a user (runs on every page load)
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);

CREATE TABLE audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       BIGINT          NOT NULL,
    action          VARCHAR(20)     NOT NULL
                    CHECK (action IN ('INSERT', 'UPDATE', 'DELETE')),
    old_values      JSONB,
    new_values      JSONB,
    performed_by    BIGINT          REFERENCES users(id),
    performed_at    TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index: find all audit entries for a specific entity
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);

-- Index: audit trail by user (who did what)
CREATE INDEX idx_audit_logs_performed_by ON audit_logs(performed_by);
