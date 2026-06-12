package com.hien.marketplace.infrastructure.stripe;

import com.hien.marketplace.infrastructure.persistence.stripe.StripeEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles Stripe event idempotency using PostgreSQL-native ON CONFLICT.
 *
 * WHY THIS COMPONENT?
 * - Encapsulates the idempotency check logic
 * - Uses PostgreSQL INSERT ... ON CONFLICT DO NOTHING for safe duplicate detection
 *
 * IMPORTANT: NO @Transactional here!
 * - The idempotency insert MUST participate in the caller's transaction
 * - If we used REQUIRES_NEW, the log row would commit independently
 * - If processEvent throws after checkAndLogEvent commits, the log row stays
 * - Stripe retries, checkAndLogEvent returns false (already logged), event is skipped forever
 * - Payment would be stuck in PROCESSING indefinitely
 *
 * SOLUTION: No transaction annotation here. The caller (processEvent) is @Transactional.
 * The INSERT ... ON CONFLICT DO NOTHING runs within that transaction.
 * If processEvent throws, both the log insert AND domain updates rollback together.
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
     * Runs within the caller's transaction (no separate transaction).
     *
     * WHY NO SEPARATE TRANSACTION?
     * - ON CONFLICT DO NOTHING returns 0 for duplicates WITHOUT throwing exception
     * - PostgreSQL transaction is NOT aborted (no need for REQUIRES_NEW)
     * - Participating in caller's transaction ensures atomic commit/rollback
     *
     * @param eventId Stripe event ID (evt_xxx)
     * @param eventType Stripe event type (e.g., "payment_intent.succeeded")
     * @return true if event was logged (new), false if duplicate (already processed)
     */
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