package com.hien.marketplace.infrastructure.persistence.stripe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for StripeEventLog entity.
 *
 * Used by webhook handler to check if event was already processed.
 *
 * WHY: Idempotency check before processing webhook.
 *
 * IMPORTANT: On PostgreSQL, catching DataIntegrityViolationException inside @Transactional
 * after a duplicate-key error leaves the transaction in an aborted state. A retry would
 * still fail because the transaction cannot proceed.
 *
 * SOLUTION: Use native INSERT ... ON CONFLICT DO NOTHING which:
 * 1. Returns 1 row inserted for new events
 * 2. Returns 0 rows for duplicates (without error)
 * 3. Does not abort the transaction
 */
@Repository
public interface StripeEventLogRepository extends JpaRepository<StripeEventLog, String> {

    /**
     * Insert event log only if not already exists.
     *
     * Uses PostgreSQL's ON CONFLICT DO NOTHING for safe idempotency.
     *
     * @param eventId Stripe event ID (evt_xxx)
     * @param eventType Stripe event type (e.g., "payment_intent.succeeded")
     * @return 1 if inserted (new event), 0 if duplicate (already processed)
     */
    @Modifying
    @Query(value = """
        INSERT INTO stripe_event_log (stripe_event_id, event_type, processed_at)
        VALUES (:eventId, :eventType, NOW())
        ON CONFLICT (stripe_event_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfNotExists(String eventId, String eventType);
}
