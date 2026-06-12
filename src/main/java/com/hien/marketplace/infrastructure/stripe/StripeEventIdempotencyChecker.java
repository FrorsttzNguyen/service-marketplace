package com.hien.marketplace.infrastructure.stripe;

import com.hien.marketplace.infrastructure.persistence.stripe.StripeEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles Stripe event idempotency using PostgreSQL-native ON CONFLICT.
 *
 * WHY SEPARATE COMPONENT?
 * - Requires REQUIRES_NEW propagation to avoid transaction abort issues
 * - PostgreSQL duplicate-key error inside a transaction aborts the entire transaction
 * - Using ON CONFLICT DO NOTHING avoids the error entirely
 * - This method runs in its own transaction, independent of the main webhook processing
 *
 * IDEMPOTENCY GUARANTEE:
 * - Returns true if event was newly logged (should process)
 * - Returns false if event already exists (skip processing)
 * - Safe for concurrent duplicate deliveries
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripeEventIdempotencyChecker {

    private final StripeEventLogRepository eventLogRepository;

    /**
     * Check if event should be processed and log it atomically.
     *
     * Uses PostgreSQL INSERT ... ON CONFLICT DO NOTHING for safe idempotency.
     *
     * @param eventId Stripe event ID (evt_xxx)
     * @param eventType Stripe event type (e.g., "payment_intent.succeeded")
     * @return true if event was logged (new), false if duplicate (already processed)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean checkAndLogEvent(String eventId, String eventType) {
        int inserted = eventLogRepository.insertIfNotExists(eventId, eventType);
        if (inserted == 0) {
            log.info("Stripe event {} already processed, skipping", eventId);
            return false;
        }
        log.debug("Stripe event {} logged for processing", eventId);
        return true;
    }
}