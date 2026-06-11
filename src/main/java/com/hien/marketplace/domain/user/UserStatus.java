package com.hien.marketplace.domain.user;

public enum UserStatus {
    ACTIVE,     // Đang hoạt động bình thường
    INACTIVE,   // Tạm ngưng (user tự deactivate)
    SUSPENDED   // Bị admin khóa (vi phạm chính sách)
}
