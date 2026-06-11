package com.hien.marketplace.domain.service;

import jakarta.persistence.*;

/**
 * Entity cho hình ảnh dịch vụ. Một service có nhiều images.
 * display_order kiểm soát thứ tự hiển thị (0 = ảnh chính).
 */
@Entity
@Table(name = "service_images")
public class ServiceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ServiceImage() {
    }

    public ServiceImage(ServiceEntity service, String url, int displayOrder) {
        this.service = service;
        this.url = url;
        this.displayOrder = displayOrder;
    }

    // Getters
    public Long getId() { return id; }
    public ServiceEntity getService() { return service; }
    public String getUrl() { return url; }
    public int getDisplayOrder() { return displayOrder; }
}
