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
 * stripe_payment_intent_id = UNIQUE để không tạo trùng local payment cho cùng PaymentIntent.
 * Webhook idempotency theo event id sẽ dùng bảng stripe_event_log ở tầng integration.
 *
 * Tại sao @Version?
 * - Stripe webhook có thể gửi nhiều event cùng lúc cho một PaymentIntent
 * - Concurrent updates cần optimistic locking để prevent lost updates
 * - Nếu 2 webhook cùng update status → one wins, one gets OptimisticLockException
 *
 * @Version hoạt động:
 * - Hibernate tự tăng version khi UPDATE
 * - Nếu version không match → OptimisticLockException
 * - Application layer retry (exponential backoff) như Booking
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

    @Embedded
    @AttributeOverride(name = "amountCents", column = @Column(name = "amount_cents", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /**
     * Optimistic locking - concurrent webhook updates.
     * Tại sao? Stripe có thể gửi nhiều webhook events cho cùng PaymentIntent:
     * - payment_intent.succeeded
     * - payment_intent.amount_capturable_updated
     * - etc.
     * Nếu 2 events cùng update Payment → race condition → version catch.
     */
    @Version
    private Long version;

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
        this.amount = amount;
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

    // Domain methods with state machine validation
    /**
     * Chuyển sang PROCESSING khi tạo PaymentIntent trên Stripe.
     * Validate: chỉ PENDING → PROCESSING được phép.
     */
    public void markAsProcessing() {
        status.throwIfInvalidTransition(PaymentStatus.PROCESSING);
        this.status = PaymentStatus.PROCESSING;
    }

    /**
     * Chuyển sang SUCCEEDED khi webhook payment_intent.succeeded.
     * Validate: chỉ PROCESSING → SUCCEEDED được phép.
     */
    public void markAsSucceeded() {
        status.throwIfInvalidTransition(PaymentStatus.SUCCEEDED);
        this.status = PaymentStatus.SUCCEEDED;
    }

    /**
     * Chuyển sang FAILED khi webhook payment_intent.payment_failed.
     * Validate: chỉ PROCESSING → FAILED được phép.
     */
    public void markAsFailed() {
        status.throwIfInvalidTransition(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
    }

    /**
     * Retry - chuyển FAILED → PENDING để tạo PaymentIntent mới.
     * Validate: chỉ FAILED → PENDING được phép.
     */
    public void resetForRetry() {
        status.throwIfInvalidTransition(PaymentStatus.PENDING);
        this.status = PaymentStatus.PENDING;
        this.stripePaymentIntentId = null;  // Clear old intent ID
    }

    public Money getAmount() { return amount; }

    // Getters
    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public PaymentStatus getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<Refund> getRefunds() { return refunds; }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}