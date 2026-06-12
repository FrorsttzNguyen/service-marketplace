package com.hien.marketplace.domain.payment;

import java.util.Map;
import java.util.Set;

/**
 * Enum cho trạng thái Refund — minh họa State Machine Pattern.
 *
 * WHY State Machine?
 * - Enforces valid transitions only
 * - Prevents bugs like SUCCEEDED → PENDING (nonsensical)
 * - Self-documenting: TRANSITIONS map shows entire lifecycle
 *
 * Refund lifecycle:
 * - PENDING: Refund request created, not yet sent to Stripe
 * - PROCESSING: Refund request sent to Stripe, awaiting response
 * - SUCCEEDED: Refund completed successfully (terminal state)
 * - FAILED: Refund failed (can retry → PENDING)
 *
 * Consistency: Matches PaymentStatus pattern for maintainability.
 */
public enum RefundStatus {
    PENDING,        // Refund created, not yet sent to Stripe
    PROCESSING,     // Refund sent to Stripe, awaiting response
    SUCCEEDED,      // Refund completed (terminal state)
    FAILED;         // Refund failed (can retry)

    /**
     * Map defining valid transitions.
     * Key = current state, Value = set of allowed next states.
     *
     * Note: PENDING → SUCCEEDED is allowed for synchronous refunds
     * where Stripe returns the result immediately (no async webhook needed).
     */
    private static final Map<RefundStatus, Set<RefundStatus>> TRANSITIONS = Map.of(
        PENDING, Set.of(PROCESSING, SUCCEEDED),  // SUCCEEDED for sync refunds
        PROCESSING, Set.of(SUCCEEDED, FAILED),
        SUCCEEDED, Set.of(),  // Terminal state - no further transitions
        FAILED, Set.of(PENDING)  // Allow retry - create new refund request
    );

    /**
     * Check if transition from current state to target is valid.
     */
    public boolean canTransitionTo(RefundStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * Validate transition. Throws exception if invalid.
     * Used in domain entity to enforce business rules.
     */
    public void throwIfInvalidTransition(RefundStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                "Cannot transition refund from " + this + " to " + target +
                ". Allowed transitions from " + this + ": " +
                TRANSITIONS.getOrDefault(this, Set.of())
            );
        }
    }
}
