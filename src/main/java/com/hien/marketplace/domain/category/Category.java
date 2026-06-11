package com.hien.marketplace.domain.category;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity cho danh mục dịch vụ (Spa, Fitness, Tutoring...).
 *
 * Self-referencing: parent_id trỏ về chính bảng categories.
 * Cho phép category cha → con: "Spa" có thể có sub-category "Facial", "Massage".
 *
 * slug dùng cho URL sạch: /services?category=spa thay vì ?categoryId=1
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Self-referencing: parent category. NULL = top-level category
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Category() {
    }

    public Category(String name, String slug) {
        this.name = name;
        this.slug = slug;
        this.createdAt = LocalDateTime.now();
    }

    public Category(String name, String slug, Category parent) {
        this(name, slug);
        this.parent = parent;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // Domain methods
    public boolean isTopLevel() {
        return parent == null;
    }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public Category getParent() { return parent; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setDescription(String description) { this.description = description; }
}
