package com.hien.marketplace.domain.vendor;

public enum VerificationStatus {
    PENDING,    // Vendor vừa đăng ký, chờ admin duyệt
    APPROVED,   // Admin đã xác minh, vendor có thể hoạt động
    REJECTED    // Bị từ chối (giấy tờ không hợp lệ)
}
