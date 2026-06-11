package com.hien.marketplace.domain.payment;

public enum PaymentStatus {
    PENDING,        // Đang chờ xử lý
    PROCESSING,     // Đang xử lý qua Stripe
    SUCCEEDED,      // Thanh toán thành công
    FAILED          // Thanh toán thất bại
}
