package com.hien.marketplace.infrastructure.persistence.stripe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for StripeEventLog entity.
 *
 * Used by webhook handler to check if event was already processed.
 *
 * WHY: Idempotency check before processing webhook.
 * Pattern:
 * 1. Check if event already processed: existsById(eventId)
 * 2. If not, save new log entry (fails if race condition)
 * 3. Process event
 */
@Repository
public interface StripeEventLogRepository extends JpaRepository<StripeEventLog, String> {
    // Inherited methods:
    // - existsById(String stripeEventId) - check if event already processed
    // - save(StripeEventLog log) - log new event (fails on duplicate key)
}
