package com.hien.marketplace.domain.provider;

public enum VerificationStatus {
    PENDING,    // Provider vừa đăng ký, chờ admin duyệt
    APPROVED,   // Admin đã xác minh, provider có thể hoạt động
    REJECTED    // Bị từ chối (giấy tờ không hợp lệ)
}
