package com.hien.marketplace.domain.service;

import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.category.Category;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.provider.Provider;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity cho dịch vụ do Provider cung cấp.
 *
 * Tên class là ServiceEntity (không phải Service) vì Spring có sẵn interface
 * org.springframework.stereotype.Service — trùng tên sẽ gây ambiguous import.
 *
 * Money được embed vào column base_price_cents để domain không dùng primitive cho khái niệm tiền.
 * PricingType enum implements Strategy Pattern cho việc tính giá.
 *
 * @NamedEntityGraph: Prevents N+1 queries when fetching services with provider/category.
 * Used in ServiceRepository for paginated queries.
 */
@Entity
@Table(name = "services")
@NamedEntityGraph(
    name = "service-with-provider-category",
    attributeNodes = {
        @NamedAttributeNode("provider"),
        @NamedAttributeNode("category")
    }
)
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Embedded
    @AttributeOverride(name = "amountCents", column = @Column(name = "base_price_cents", nullable = false))
    private Money basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type", nullable = false, length = 20)
    private PricingType pricingType;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status;

    // === Denormalized fields for search/filter performance ===

    /**
     * City denormalized from provider.address.city.
     *
     * WHY: Allows efficient filtering by city without joining through provider.
     * Kept in sync when provider's address changes (handled by domain events or service layer).
     */
    @Column(length = 100)
    private String city;

    /**
     * Average rating calculated from reviews.
     *
     * WHY: Allows efficient sorting/filtering by rating without subquery.
     * Updated when reviews are added/modified/deleted.
     * NULL means no reviews yet (different from 0.00 which would be an actual average).
     */
    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // === Relationships ===

    // CascadeType.ALL: xóa service → xóa luôn images + availability
    // orphanRemoval = true: gỡ image khỏi list → tự động xóa image đó khỏi DB
    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceAvailability> availability = new ArrayList<>();

    protected ServiceEntity() {
    }

    public ServiceEntity(Provider provider, String name, Money basePrice, PricingType pricingType, int durationMinutes) {
        this.provider = provider;
        this.name = name;
        this.basePrice = basePrice;
        this.pricingType = pricingType;
        this.durationMinutes = durationMinutes;
        this.status = ServiceStatus.DRAFT;
        // Denormalize city from provider's address for efficient filtering
        if (provider.getAddress() != null) {
            this.city = provider.getAddress().getCity();
        }
        this.averageRating = null; // No reviews initially
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

    // === Domain methods ===

    /**
     * Tính giá cuối cùng cho booking dựa trên PricingType strategy.
     * Caller không cần biết loại giá — chỉ gọi method này.
     */
    public Money calculatePrice() {
        return pricingType.calculatePrice(basePrice, durationMinutes);
    }

    public void activate() {
        this.status = ServiceStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ServiceStatus.INACTIVE;
    }

    public boolean isActive() {
        return this.status == ServiceStatus.ACTIVE;
    }

    public void addImage(ServiceImage image) {
        this.images.add(image);
    }

    public void removeImage(ServiceImage image) {
        this.images.remove(image);
    }

    /**
     * Update city when provider's address changes.
     *
     * WHY: City is denormalized for efficient filtering.
     * Called when provider moves to a different city.
     */
    public void updateCity(String newCity) {
        this.city = newCity;
    }

    /**
     * Update average rating when reviews change.
     *
     * WHY: Average rating is denormalized for efficient sorting/filtering.
     * Called when reviews are added/modified/deleted.
     *
     * @param newRatingAvg New average rating (null if no reviews, BigDecimal if has reviews)
     */
    public void updateAverageRating(BigDecimal newRatingAvg) {
        this.averageRating = newRatingAvg;
    }

    // === Getters ===

    public Long getId() { return id; }
    public Provider getProvider() { return provider; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Money getBasePrice() { return basePrice; }
    public PricingType getPricingType() { return pricingType; }
    public int getDurationMinutes() { return durationMinutes; }
    public ServiceStatus getStatus() { return status; }
    public String getCity() { return city; }
    public BigDecimal getAverageRating() { return averageRating; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<ServiceImage> getImages() { return images; }
    public List<ServiceAvailability> getAvailability() { return availability; }

    // === Setters (mutable fields only) ===

    public void setDescription(String description) { this.description = description; }
    public void setCategory(Category category) { this.category = category; }
    public void setBasePrice(Money basePrice) { this.basePrice = basePrice; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
}
