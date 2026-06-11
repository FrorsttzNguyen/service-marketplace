package com.hien.marketplace.domain.service;

public enum ServiceStatus {
    DRAFT,      // Đang soạn, chưa public
    ACTIVE,     // Đang hiển thị cho customer
    INACTIVE    // Tạm ẩn (vendor tự ẩn)
}
