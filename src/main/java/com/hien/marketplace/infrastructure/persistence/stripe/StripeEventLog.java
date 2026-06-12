package com.hien.marketplace.infrastructure.persistence.stripe;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity for tracking processed Stripe webhook events.
 *
 * WHY: Stripe delivers webhooks with at-least-once semantics.
 * Same event may be delivered multiple times.
 * This table ensures idempotency - each event processed exactly once.
 *
 * HOW: stripe_event_id is PRIMARY KEY.
 * First INSERT succeeds, subsequent INSERTs fail with duplicate key.
 * Webhook handler catches exception and returns 200 OK (already processed).
 *
 * Table created by V5 migration: CREATE TABLE stripe_event_log (...)
 */
@Entity
@Table(name = "stripe_event_log")
@Getter
@Setter
@NoArgsConstructor
public class StripeEventLog {

    @Id
    @Column(name = "stripe_event_id", length = 255)
    private String stripeEventId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    /**
     * Constructor for new event log entry.
     *
     * @param stripeEventId Stripe event ID (evt_xxx)
     * @param eventType Stripe event type (e.g., "payment_intent.succeeded")
     */
    public StripeEventLog(String stripeEventId, String eventType) {
        this.stripeEventId = stripeEventId;
        this.eventType = eventType;
        this.processedAt = LocalDateTime.now();
    }
}
