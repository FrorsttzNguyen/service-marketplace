package com.hien.marketplace.domain.provider;

import com.hien.marketplace.domain.common.Address;
import com.hien.marketplace.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity đại diện cho nhà cung cấp dịch vụ.
 *
 * COMPOSITION (không phải Inheritance): Provider HAS-A User.
 * - @OneToOne User = mỗi user có tối đa một provider profile theo unique index trong DB
 * - User có thể vừa CUSTOMER vừa VENDOR (nếu mở provider profile)
 * - Xóa provider profile không ảnh hưởng user account
 *
 * Nếu dùng inheritance (Provider extends User):
 * - User vĩnh viễn là Provider, không đổi được
 * - Một user không thể vừa customer vừa provider
 */
@Entity
@Table(name = "providers")
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Composition: Provider HAS-A User (không extends User).
    // @OneToOne khớp với unique index providers.user_id: một user chỉ có một provider profile.
    @OneToOne(fetch = FetchType.LAZY) // LAZY = không load User mỗi khi load Provider
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(columnDefinition = "TEXT")
    private String description;

    // @Embedded = Address fields lưu thẳng vào bảng providers (không có bảng addresses riêng)
    @Embedded
    private Address address;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Provider() {
    }

    public Provider(User user, String businessName) {
        this.user = user;
        this.businessName = businessName;
        this.ratingAvg = BigDecimal.ZERO;
        this.verificationStatus = VerificationStatus.PENDING;
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

    public void approve() {
        this.verificationStatus = VerificationStatus.APPROVED;
    }

    public void reject() {
        this.verificationStatus = VerificationStatus.REJECTED;
    }

    public boolean isApproved() {
        return this.verificationStatus == VerificationStatus.APPROVED;
    }

    public void updateRating(BigDecimal newRatingAvg) {
        this.ratingAvg = newRatingAvg;
    }

    // === Getters ===

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getBusinessName() { return businessName; }
    public String getDescription() { return description; }
    public Address getAddress() { return address; }
    public String getWebsiteUrl() { return websiteUrl; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // === Setters (chỉ cho mutable fields) ===

    public void setDescription(String description) { this.description = description; }
    public void setAddress(Address address) { this.address = address; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }
}
