package com.hien.marketplace.domain.vendor;

import com.hien.marketplace.domain.common.Address;
import com.hien.marketplace.domain.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity đại diện cho nhà cung cấp dịch vụ.
 *
 * COMPOSITION (không phải Inheritance): Vendor HAS-A User.
 * - @ManyToOne User = vendor "có" một user, không "là" user
 * - User có thể vừa CUSTOMER vừa VENDOR (nếu mở vendor profile)
 * - Xóa vendor profile không ảnh hưởng user account
 *
 * Nếu dùng inheritance (Vendor extends User):
 * - User vĩnh viễn là Vendor, không đổi được
 * - Một user không thể vừa customer vừa vendor
 */
@Entity
@Table(name = "vendors")
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Composition: Vendor HAS-A User (không extends User)
    @ManyToOne(fetch = FetchType.LAZY) // LAZY = không load User mỗi khi load Vendor
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(columnDefinition = "TEXT")
    private String description;

    // @Embedded = Address fields lưu thẳng vào bảng vendors (không có bảng addresses riêng)
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

    protected Vendor() {
    }

    public Vendor(User user, String businessName) {
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
