package com.hien.marketplace.domain.notification;

public enum NotificationType {
    BOOKING_CONFIRMED,   // Booking được vendor xác nhận
    BOOKING_CANCELLED,   // Booking bị hủy
    PAYMENT_RECEIVED,    // Thanh toán thành công
    PAYMENT_FAILED,      // Thanh toán thất bại
    REVIEW_RECEIVED,     // Nhận review mới (dành cho vendor)
    VENDOR_APPROVED,     // Vendor profile được admin duyệt
    VENDOR_REJECTED      // Vendor profile bị từ chối
}
