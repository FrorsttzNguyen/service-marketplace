package com.hien.marketplace.infrastructure.stripe;

import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.application.exception.StripeApiException;
import com.hien.marketplace.application.service.PaymentService;
import com.hien.marketplace.domain.payment.events.PaymentFailedEvent;
import com.hien.marketplace.domain.payment.events.PaymentSucceededEvent;
import com.hien.marketplace.infrastructure.persistence.stripe.StripeEventLog;
import com.hien.marketplace.infrastructure.persistence.stripe.StripeEventLogRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for Stripe webhook events.
 *
 * RESPONSIBILITIES:
 * 1. Verify Stripe signature (security)
 * 2. Ensure idempotent processing (deduplication)
 * 3. Dispatch events to appropriate handlers
 * 4. Publish domain events for async processing
 *
 * CRITICAL: Webhook endpoint is PUBLIC (no JWT authentication).
 * Security comes from signature verification using webhook secret.
 *
 * IDEMPOTENCY PATTERN:
 * 1. Receive webhook
 * 2. Verify signature
 * 3. Try INSERT into stripe_event_log (primary key = event ID)
 *    - Success: process event
 *    - Duplicate key: already processed, return success
 * 4. Process event and update domain
 * 5. Return 200 OK
 *
 * WHY @Transactional on processEvent():
 * - Ensures event log INSERT and domain UPDATE are atomic
 * - If domain update fails, event log INSERT rolls back
 * - Next webhook delivery will retry
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookHandler {

    private final StripeConfig stripeConfig;
    private final StripeEventLogRepository eventLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentService paymentService;

    /**
     * Verify Stripe webhook signature.
     *
     * CRITICAL for security - prevents forgery.
     * Stripe signs webhook payload with webhook secret.
     * Verification ensures:
     * - Payload wasn't tampered with
     * - Webhook came from Stripe (not attacker)
     *
     * @param payload Raw request body (must be raw, not parsed)
     * @param sigHeader Stripe-Signature header value
     * @return Verified Stripe Event
     * @throws StripeApiException if signature verification fails
     */
    public Event verifySignature(String payload, String sigHeader) {
        try {
            return Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed", e);
            throw new StripeApiException("Invalid webhook signature", e);
        }
    }

    /**
     * Process Stripe webhook event with idempotency.
     *
     * IDEMPOTENCY: Ensures each event is processed exactly once,
     * even if Stripe delivers it multiple times.
     *
     * TRANSACTION SEMANTICS:
     * - Event log INSERT and domain UPDATE are in same transaction
     * - If domain update fails for SUPPORTED events, exception is rethrown
     * - Transaction rolls back, including event log INSERT
     * - Stripe will retry the webhook
     *
     * @param event Verified Stripe event
     */
    @Transactional
    public void processEvent(Event event) {
        String eventId = event.getId();
        String eventType = event.getType();

        log.info("Processing Stripe event: id={}, type={}", eventId, eventType);

        // IDEMPOTENCY: Log event to prevent duplicate processing
        // Use saveAndFlush to detect duplicate immediately, not at transaction commit
        try {
            eventLogRepository.saveAndFlush(new StripeEventLog(eventId, eventType));
            log.debug("Event {} logged successfully", eventId);
        } catch (DataIntegrityViolationException e) {
            // Primary key violation = event already processed
            log.info("Event {} already processed, skipping", eventId);
            return;
        }

        // Dispatch to appropriate handler based on event type
        // IMPORTANT: Do NOT catch and swallow exceptions from supported events
        // Let them propagate to trigger transaction rollback and Stripe retry
        switch (eventType) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentPaymentFailed(event);
            default -> log.debug("Unhandled event type: {} - safe to ignore", eventType);
        }
    }

    /**
     * Handle payment_intent.succeeded event.
     *
     * This is called by Stripe when a customer successfully completes payment.
     * We should:
     * 1. Find local Payment by stripePaymentIntentId
     * 2. Update status to SUCCEEDED
     * 3. Update Order status to PAID
     * 4. Publish PaymentSucceededEvent for notifications
     *
     * IMPORTANT: This is a SUPPORTED event. Exceptions will be propagated
     * to processEvent() and cause transaction rollback + Stripe retry.
     *
     * Separation of concerns:
     * - WebhookHandler: signature verification, idempotency, dispatching
     * - PaymentService: domain logic, status updates, event publishing
     */
    private void handlePaymentIntentSucceeded(Event event) {
        log.info("Handling payment_intent.succeeded event");

        String paymentIntentId = extractPaymentIntentId(event);

        if (paymentIntentId == null) {
            log.error("Failed to extract PaymentIntent ID from event {}", event.getId());
            throw new IllegalStateException("Failed to extract PaymentIntent ID from event");
        }

        log.info("PaymentIntent {} succeeded, triggering payment update", paymentIntentId);

        // Call PaymentService to update domain state
        // This will throw if payment not found or update fails
        paymentService.handlePaymentSucceeded(paymentIntentId);
    }

    /**
     * Handle payment_intent.payment_failed event.
     *
     * Called when payment fails (e.g., card declined, insufficient funds).
     * We should:
     * 1. Find local Payment by stripePaymentIntentId
     * 2. Update status to FAILED
     * 3. Publish PaymentFailedEvent for notifications
     *
     * IMPORTANT: This is a SUPPORTED event. Exceptions will be propagated
     * to processEvent() and cause transaction rollback + Stripe retry.
     */
    private void handlePaymentIntentPaymentFailed(Event event) {
        log.info("Handling payment_intent.payment_failed event");

        String paymentIntentId = extractPaymentIntentId(event);

        if (paymentIntentId == null) {
            log.error("Failed to extract PaymentIntent ID from event {}", event.getId());
            throw new IllegalStateException("Failed to extract PaymentIntent ID from event");
        }

        // Extract failure information from event
        String failureReason = extractFailureMessage(event);
        String failureCode = extractFailureCode(event);

        log.warn("PaymentIntent {} failed: {} (code: {})",
                paymentIntentId, failureReason, failureCode);

        // Call PaymentService to update domain state
        // This will throw if payment not found or update fails
        paymentService.handlePaymentFailed(paymentIntentId, failureReason, failureCode);
    }

    /**
     * Extract PaymentIntent ID from Stripe event.
     *
     * In Stripe SDK v24+, we can access the event data directly.
     */
    private String extractPaymentIntentId(Event event) {
        try {
            // The event data object contains the PaymentIntent
            if (event.getData() != null && event.getData().getObject() != null) {
                // Cast to PaymentIntent
                if (event.getData().getObject() instanceof PaymentIntent) {
                    PaymentIntent paymentIntent = (PaymentIntent) event.getData().getObject();
                    return paymentIntent.getId();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract PaymentIntent ID: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract failure message from payment_intent.payment_failed event.
     */
    private String extractFailureMessage(Event event) {
        try {
            if (event.getData() != null && event.getData().getObject() instanceof PaymentIntent) {
                PaymentIntent paymentIntent = (PaymentIntent) event.getData().getObject();
                if (paymentIntent.getLastPaymentError() != null) {
                    return paymentIntent.getLastPaymentError().getMessage();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract failure message: {}", e.getMessage());
        }
        return "Unknown error";
    }

    /**
     * Extract failure code from payment_intent.payment_failed event.
     */
    private String extractFailureCode(Event event) {
        try {
            if (event.getData() != null && event.getData().getObject() instanceof PaymentIntent) {
                PaymentIntent paymentIntent = (PaymentIntent) event.getData().getObject();
                if (paymentIntent.getLastPaymentError() != null) {
                    return paymentIntent.getLastPaymentError().getCode();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract failure code: {}", e.getMessage());
        }
        return "unknown";
    }
}
