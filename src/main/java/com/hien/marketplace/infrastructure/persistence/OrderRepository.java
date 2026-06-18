package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.order.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByBookingId(Long bookingId);

    @Query("select o from Order o where o.booking.vendor.id = :vendorId")
    List<Order> findByVendorId(Long vendorId);

    /**
     * Find order by ID with pessimistic write lock.
     *
     * Used during payment creation to prevent concurrent modifications.
     * Lock is held until the transaction commits.
     *
     * @param orderId Order ID
     * @return Order with lock, or empty if not found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(Long orderId);
}
