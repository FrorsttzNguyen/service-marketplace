package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.review.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByVendorId(Long vendorId);

    List<Review> findByServiceId(Long serviceId);

    boolean existsByBookingId(Long bookingId);

    /** Number of reviews for a service — feeds ServiceResponse.totalReviews (catalog/detail). */
    long countByServiceId(Long serviceId);
}
