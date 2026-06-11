package com.hien.marketplace.domain.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho người dùng trong hệ thống.
 *
 * Một User có thể đóng nhiều vai trò (thông qua UserRole):
 * - CUSTOMER: đặt dịch vụ
 * - VENDOR: cung cấp dịch vụ (tạo Vendor profile riêng)
 * - ADMIN: quản lý hệ thống
 *
 * Composition: Vendor HAS-A User (không phải extends User).
 * Nếu dùng inheritance, user không thể vừa là customer vừa là vendor.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash; // BCrypt hash — KHÔNG bao giờ lưu plaintext

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING) // STRING = lưu tên enum ("CUSTOMER"), không phải số (0,1,2)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // === Constructors ===

    // No-arg constructor cho JPA (Hibernate dùng reflection)
    protected User() {
    }

    public User(String email, String passwordHash, String fullName, UserRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // === Lifecycle callbacks — tự động set timestamps ===

    @PrePersist // Chạy trước khi INSERT lần đầu
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate // Chạy trước mỗi lần UPDATE
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // === Domain methods — business logic nằm trong entity ===

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    // === Getters ===

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
