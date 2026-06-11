package com.hien.marketplace.domain.payment;

import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.order.Order;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity cho thanh toán. Liên kết với Stripe PaymentIntent.
 *
 * stripe_payment_intent_id = UNIQUE để webhook idempotent:
 * Stripe gửi cùng event nhiều lần → chỉ process 1 lần (check unique constraint).
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "stripe_payment_intent_id", unique = true, length = 255)
    private String stripePaymentIntentId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Refund> refunds = new ArrayList<>();

    protected Payment() {
    }

    public Payment(Order order, Money amount) {
        this.order = order;
        this.amountCents = amount.getAmountCents();
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Domain methods
    public void markAsProcessing() { this.status = PaymentStatus.PROCESSING; }
    public void markAsSucceeded() { this.status = PaymentStatus.SUCCEEDED; }
    public void markAsFailed() { this.status = PaymentStatus.FAILED; }

    public Money getAmount() { return Money.of(amountCents); }

    // Getters
    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public PaymentStatus getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<Refund> getRefunds() { return refunds; }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}
