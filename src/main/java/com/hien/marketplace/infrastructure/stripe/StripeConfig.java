package com.hien.marketplace.infrastructure.stripe;

import com.stripe.Stripe;
import com.stripe.net.RequestOptions;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for Stripe API integration.
 *
 * WHY use @ConfigurationProperties:
 * - Type-safe: Compile-time checking, IDE autocomplete
 * - Validation: @NotBlank ensures required properties are set
 * - Centralized: All Stripe config in one place
 * - Testable: Easy to mock in tests
 *
 * WHY @PostConstruct for initialization:
 * - Stripe.apiKey is a static field in Stripe SDK
 * - Must be set before any Stripe API calls
 * - @PostConstruct runs after Spring creates the bean
 *
 * Environment variables needed:
 * - STRIPE_API_KEY: Secret key from Stripe Dashboard (sk_test_... or sk_live_...)
 * - STRIPE_WEBHOOK_SECRET: Webhook signing secret (whsec_...)
 */
@Configuration
@ConfigurationProperties(prefix = "stripe")
@Validated
@Getter
@Setter
public class StripeConfig {

    @NotBlank(message = "Stripe API key is required")
    private String apiKey;

    @NotBlank(message = "Stripe webhook secret is required")
    private String webhookSecret;

    /**
     * Initialize Stripe SDK with API key.
     * Called automatically after Spring creates this bean.
     *
     * WHY: Stripe SDK requires static initialization.
     * Setting Stripe.apiKey here ensures it's done before any API calls.
     */
    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
    }

    /**
     * Create RequestOptions with idempotency key.
     *
     * WHY IDEMPOTENCY:
     * - Prevents duplicate operations on network retry
     * - Stripe stores idempotency keys for 24 hours
     * - Same key = same response, even if called multiple times
     *
     * USAGE:
     * - PaymentIntent creation: "order_{orderId}"
     * - Refund creation: "refund_{orderId}_{timestamp}"
     *
     * @param idempotencyKey Unique key for this operation
     * @return RequestOptions with idempotency key set
     */
    public RequestOptions getRequestOptions(String idempotencyKey) {
        return RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
    }
}
