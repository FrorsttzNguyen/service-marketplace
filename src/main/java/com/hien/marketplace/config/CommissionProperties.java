package com.hien.marketplace.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Commission configuration — the platform fee charged on top of a service's price.
 *
 * WHY @ConfigurationProperties (not a bare @Value): matches how the project reads other
 * app.* config (see StripeConfig). Type-safe, validated, and easy to inject into
 * OrderService as a normal bean instead of scattering a magic {@code ${app.commission.rate}}
 * placeholder through the code.
 *
 * The rate is a fraction (0.10 = 10%), NOT a percentage. It's applied to an order's
 * subtotal in OrderService#createFromBooking, computed in CENTS via Money to avoid any
 * floating-point money math (see the formula there).
 *
 * Bound to the {@code app.commission.*} keys in application.yml / application-prod.yml.
 * Override per-environment with APP_COMMISSION_RATE (Spring's relaxed binding).
 */
@Configuration
@ConfigurationProperties(prefix = "app.commission")
@Validated
@Getter
@Setter
public class CommissionProperties {

    /**
     * Platform commission as a fraction of the subtotal.
     * 0.10 = 10%. Must be in [0.0, 1.0]: 0 means a free listing, 1 (edge case) means the
     * platform takes the whole subtotal as commission. Defaults to 0.10.
     */
    @NotNull
    @DecimalMin(value = "0.0", message = "Commission rate cannot be negative")
    @DecimalMax(value = "1.0", message = "Commission rate cannot exceed 100%")
    private BigDecimal rate = new BigDecimal("0.10");
}
