package com.hien.marketplace.domain.payment;

public enum RefundStatus {
    PENDING,        // Đang chờ hoàn tiền
    SUCCEEDED,      // Hoàn tiền thành công
    FAILED          // Hoàn tiền thất bại
}
