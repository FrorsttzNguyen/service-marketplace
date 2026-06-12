package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.infrastructure.stripe.StripeWebhookHandler;
import com.stripe.model.Event;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Stripe webhook events.
 *
 * CRITICAL: This endpoint is PUBLIC (no JWT authentication).
 * Security comes from Stripe signature verification.
 *
 * WHY PUBLIC?
 * - Stripe sends webhooks from their servers (no JWT)
 * - We verify authenticity via signature using webhook secret
 * - Signature verification ensures request came from Stripe
 *
 * SECURITY:
 * 1. Stripe signs webhook payload with webhook secret
 * 2. We verify signature using Webhook.constructEvent()
 * 3. Invalid signature → reject request
 *
 * IDEMPOTENCY:
 * - Each Stripe event has unique ID (evt_xxx)
 * - We log event ID in stripe_event_log table
 * - Duplicate events are detected and skipped
 *
 * SUPPORTED EVENTS:
 * - payment_intent.succeeded → Payment succeeded
 * - payment_intent.payment_failed → Payment failed
 *
 * RESPONSE:
 * - 200 OK: Event processed successfully
 * - 400 Bad Request: Invalid signature or payload
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Third-party webhook endpoints")
@Slf4j
public class StripeWebhookController {

    private final StripeWebhookHandler webhookHandler;

    /**
     * Handle Stripe webhook events.
     *
     * CRITICAL: This endpoint must return quickly (within 5 seconds).
     * Stripe retries webhooks that timeout or return errors.
     *
     * FLOW:
     * 1. Receive raw payload and signature header
     * 2. Verify signature (reject if invalid)
     * 3. Check idempotency (skip if already processed)
     * 4. Process event
     * 5. Return 200 OK
     *
     * IMPORTANT: Must use raw request body (not parsed JSON).
     * Signature verification requires exact payload bytes.
     *
     * @param payload Raw request body (JSON string)
     * @param sigHeader Stripe-Signature header value
     * @return 200 OK on success, 400 on error
     */
    @PostMapping("/stripe")
    @Operation(
            summary = "Stripe webhook",
            description = "Handle Stripe payment events. This endpoint is PUBLIC (no JWT). " +
                    "Security comes from Stripe signature verification."
    )
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        log.debug("Received Stripe webhook");

        try {
            // Step 1: Verify signature (CRITICAL for security)
            Event event = webhookHandler.verifySignature(payload, sigHeader);
            log.info("Verified Stripe webhook: type={}, id={}", event.getType(), event.getId());

            // Step 2: Process event with idempotency
            webhookHandler.processEvent(event);

            // Return 200 OK quickly
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing Stripe webhook: {}", e.getMessage(), e);
            // Return 400 for invalid signature or processing errors
            // Stripe will retry on 5xx errors but not on 4xx
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
