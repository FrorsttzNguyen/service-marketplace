package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByBookingId(Long bookingId);
}
