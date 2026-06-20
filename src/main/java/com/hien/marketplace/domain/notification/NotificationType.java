package com.hien.marketplace.domain.notification;

public enum NotificationType {
    BOOKING_CONFIRMED,   // Booking được provider xác nhận
    BOOKING_CANCELLED,   // Booking bị hủy
    PAYMENT_RECEIVED,    // Thanh toán thành công
    PAYMENT_FAILED,      // Thanh toán thất bại
    REVIEW_RECEIVED,     // Nhận review mới (dành cho provider)
    VENDOR_APPROVED,     // Provider profile được admin duyệt
    VENDOR_REJECTED      // Provider profile bị từ chối
}
