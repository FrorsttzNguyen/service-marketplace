package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.review.Review;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.provider.Provider;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.ReviewRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.ProviderRepository;
import com.hien.marketplace.interfaces.dto.request.ReviewCreateRequest;
import com.hien.marketplace.interfaces.dto.response.ReviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Service for review operations — the last step of the booking lifecycle.
 *
 * WHY: a customer can review a service only after the booking they made for it is COMPLETED.
 * Creating a review denormalizes the rating onto the service (averageRating) and the provider
 * (ratingAvg) so the catalog can sort/filter by rating without a subquery.
 *
 * Authorization + business rules live here (not the controller), mirroring OrderService /
 * BookingService: ownership and state violations surface as BusinessRuleViolationException -> 422;
 * a missing booking is ResourceNotFoundException -> 404.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    // Provider.ratingAvg + ServiceEntity.averageRating are precision 3 scale 2 (e.g. 4.33).
    private static final int RATING_SCALE = 2;

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final ServiceRepository serviceRepository;
    private final ProviderRepository providerRepository;

    /**
     * Create a review for a completed booking.
     *
     * Flow:
     * 1. Load the booking (404 if missing).
     * 2. Authorize: caller must be the booking's CUSTOMER (422 — mirrors cancelBooking ownership).
     * 3. Business rule: booking must be COMPLETED (422).
     * 4. Business rule: one review per booking — reject if already reviewed (422; also enforced by
     *    the UNIQUE(booking_id) DB constraint as a backstop).
     * 5. Persist the review, then recompute the service + provider rating averages.
     */
    @Transactional
    public ReviewResponse createReview(Long customerId, ReviewCreateRequest request) {
        // Step 1: load the booking
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", request.bookingId()));

        // Step 2: only the customer who made the booking can review it
        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new BusinessRuleViolationException(
                    "Review ownership",
                    "You can only review your own bookings"
            );
        }

        // Step 3: the service must actually have been delivered
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessRuleViolationException(
                    "Booking status",
                    "You can only review a completed booking"
            );
        }

        // Step 4: one review per booking
        if (reviewRepository.existsByBookingId(booking.getId())) {
            throw new BusinessRuleViolationException(
                    "Duplicate review",
                    "This booking has already been reviewed"
            );
        }

        // Step 5: persist + recompute aggregates
        User customer = booking.getCustomer();
        Provider provider = booking.getProvider();
        ServiceEntity service = booking.getService();

        Review review = new Review(booking, customer, provider, service,
                request.rating(), request.comment());
        review = reviewRepository.save(review);

        recomputeServiceRating(service);
        recomputeProviderRating(provider);

        return toResponse(review);
    }

    /**
     * List reviews for a service, newest first. Public — shown on the service detail page.
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> getServiceReviews(Long serviceId) {
        return reviewRepository.findByServiceId(serviceId).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * List reviews for a provider, newest first. Public — shown on the provider profile.
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> getProviderReviews(Long providerId) {
        return reviewRepository.findByProviderId(providerId).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    // === Private helpers ===

    /**
     * Recompute the denormalized averageRating for a service from all its reviews.
     * Querying after the save auto-flushes the persistence context, so the new review is included.
     */
    private void recomputeServiceRating(ServiceEntity service) {
        BigDecimal avg = average(reviewRepository.findByServiceId(service.getId()));
        service.updateAverageRating(avg);
        serviceRepository.save(service);
    }

    private void recomputeProviderRating(Provider provider) {
        BigDecimal avg = average(reviewRepository.findByProviderId(provider.getId()));
        // Provider.ratingAvg is NOT NULL (defaults to ZERO), so never store null here.
        provider.updateRating(avg != null ? avg : BigDecimal.ZERO);
        providerRepository.save(provider);
    }

    /**
     * Mean rating over a list of reviews, rounded HALF_UP to 2 decimals.
     * Returns null for an empty list (no reviews yet).
     */
    private BigDecimal average(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return null;
        }
        int sum = reviews.stream().mapToInt(Review::getRating).sum();
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(reviews.size()), RATING_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Build the response, enriching the names the MapStruct mapper can't derive cleanly.
     */
    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getId(),
                review.getService().getId(),
                review.getService().getName(),
                review.getCustomer().getId(),
                review.getCustomer().getFullName(),
                review.getProvider().getId(),
                review.getProvider().getBusinessName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
