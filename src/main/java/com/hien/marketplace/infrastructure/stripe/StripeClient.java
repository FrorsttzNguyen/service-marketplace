package com.hien.marketplace.infrastructure.stripe;

import com.hien.marketplace.domain.common.Money;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Wrapper for Stripe API calls.
 *
 * CRITICAL ARCHITECTURE DECISION:
 * All Stripe API calls should be made OUTSIDE @Transactional boundaries.
 *
 * WHY?
 * 1. Network I/O should never be inside a database transaction
 *    - Stripe API call can take 100ms-2s (network latency)
 *    - Database transaction would be held open for that duration
 *    - Database locks held during API call = connection pool exhaustion
 *
 * 2. Failure handling
 *    - If Stripe succeeds but DB transaction rolls back = orphan PaymentIntent
 *    - If Stripe fails and DB rolls back = clean state, can retry
 *    - Better: Create PaymentIntent first (outside tx), then save Payment (inside tx)
 *
 * 3. Timeout handling
 *    - Stripe API has its own timeout (default 30s)
 *    - Database transactions should be short (ideally <100ms)
 *    - Long transactions = connection pool starvation
 *
 * Pattern:
 * ```
 * // 1. Create PaymentIntent (OUTSIDE transaction)
 * PaymentIntent intent = stripeClient.createPaymentIntent(amount, orderId);
 *
 * // 2. Save Payment with intent ID (INSIDE transaction)
 * @Transactional
 * payment.setStripePaymentIntentId(intent.getId());
 * paymentRepository.save(payment);
 * ```
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripeClient {

    /**
     * Create a PaymentIntent on Stripe.
     *
     * CRITICAL: Call this OUTSIDE @Transactional.
     *
     * @param amount Payment amount (Money value object)
     * @param orderId Order ID for metadata (helps with reconciliation)
     * @return Stripe PaymentIntent with client_secret for frontend
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent createPaymentIntent(Money amount, Long orderId) throws StripeException {
        log.info("Creating PaymentIntent for order {}: amount={} cents", orderId, amount.getAmountCents());

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.getAmountCents())
                .setCurrency("usd")
                .putMetadata("order_id", orderId.toString())
                // Automatic payment methods allow Stripe to use all enabled methods
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        PaymentIntent intent = PaymentIntent.create(params);

        log.info("Created PaymentIntent {}: status={}", intent.getId(), intent.getStatus());
        return intent;
    }

    /**
     * Retrieve a PaymentIntent by ID.
     *
     * Used by webhook handlers to get latest status.
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     * @return PaymentIntent object
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        log.debug("Retrieving PaymentIntent {}", paymentIntentId);
        return PaymentIntent.retrieve(paymentIntentId);
    }

    /**
     * Create a refund on Stripe.
     *
     * CRITICAL: Call this OUTSIDE @Transactional.
     *
     * @param paymentIntentId Stripe PaymentIntent ID to refund
     * @param amountCents Refund amount in cents (null for full refund)
     * @return Stripe Refund object
     * @throws StripeException if Stripe API call fails
     */
    public Refund createRefund(String paymentIntentId, Long amountCents) throws StripeException {
        log.info("Creating refund for PaymentIntent {}: amount={} cents",
                paymentIntentId, amountCents != null ? amountCents : "full");

        RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId);

        // If amountCents is null, Stripe refunds the full amount
        if (amountCents != null) {
            paramsBuilder.setAmount(amountCents);
        }

        Refund refund = Refund.create(paramsBuilder.build());

        log.info("Created Refund {}: status={}", refund.getId(), refund.getStatus());
        return refund;
    }

    /**
     * Cancel a PaymentIntent.
     *
     * Used when order is cancelled before payment completes.
     *
     * @param paymentIntentId Stripe PaymentIntent ID to cancel
     * @return Cancelled PaymentIntent
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent cancelPaymentIntent(String paymentIntentId) throws StripeException {
        log.info("Cancelling PaymentIntent {}", paymentIntentId);

        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        PaymentIntent cancelledIntent = intent.cancel();

        log.info("Cancelled PaymentIntent {}: status={}", cancelledIntent.getId(), cancelledIntent.getStatus());
        return cancelledIntent;
    }
}
