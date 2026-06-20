package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.review.Review;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.ReviewRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.VendorRepository;
import com.hien.marketplace.interfaces.dto.request.ReviewCreateRequest;
import com.hien.marketplace.interfaces.dto.response.ReviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviewService — the last step of the booking lifecycle (Phase 7 Slice 5).
 *
 * WHY test the service: the real logic — ownership, the COMPLETED status gate, one-review-per-booking,
 * and the denormalized rating recompute — lives here. Mocking the repositories lets us assert each
 * branch precisely without a DB.
 *
 * MOCKING mirrors PaymentServiceTest: domain objects are spied so we can stub getId() (entities have no
 * id setter; JPA sets it). LENIENT settings allow the shared @BeforeEach stubs to go unused per test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private VendorRepository vendorRepository;

    private ReviewService reviewService;

    private static final Long CUSTOMER_ID = 1L;
    private static final Long BOOKING_ID = 10L;
    private static final Long SERVICE_ID = 20L;
    private static final Long VENDOR_ID = 30L;
    private static final Long REVIEW_ID = 100L;

    private User customer;
    private User vendorUser;
    private Vendor vendor;
    private ServiceEntity service;
    private Booking completedBooking;
    private Booking pendingBooking;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, bookingRepository,
                serviceRepository, vendorRepository);

        customer = spy(new User("customer@example.com", "hashed", "Customer", UserRole.CUSTOMER));
        when(customer.getId()).thenReturn(CUSTOMER_ID);

        vendorUser = new User("vendor@example.com", "hashed", "Vendor", UserRole.VENDOR);
        vendor = spy(new Vendor(vendorUser, "Vendor Biz"));
        when(vendor.getId()).thenReturn(VENDOR_ID);

        service = spy(new ServiceEntity(vendor, "Test Service", Money.of(10000), PricingType.FIXED, 60));
        when(service.getId()).thenReturn(SERVICE_ID);

        // A booking driven all the way to COMPLETED.
        // New lifecycle: PENDING → CONFIRMED → PAID → IN_PROGRESS → COMPLETED
        Booking completed = new Booking(service, customer, vendor, LocalDate.now(),
                LocalTime.of(10, 0), LocalTime.of(11, 0), Money.of(10000), Money.of(1000));
        completed.confirm(vendorUser);
        completed.markAsPaid(null);  // Stripe webhook step (changedBy null = system)
        completed.start(vendorUser);
        completed.complete(vendorUser);
        completedBooking = spy(completed);
        when(completedBooking.getId()).thenReturn(BOOKING_ID);

        // A still-PENDING booking for the status-gate test.
        Booking pending = new Booking(service, customer, vendor, LocalDate.now(),
                LocalTime.of(12, 0), LocalTime.of(13, 0), Money.of(10000), Money.of(1000));
        pendingBooking = spy(pending);
        when(pendingBooking.getId()).thenReturn(BOOKING_ID);
    }

    // === createReview =======================================================

    @Nested
    @DisplayName("createReview")
    class CreateReview {

        @Test
        @DisplayName("happy path: COMPLETED booking → review saved + service/vendor ratings recomputed")
        void shouldCreateReviewAndRecomputeRatings() {
            ReviewCreateRequest request = new ReviewCreateRequest(BOOKING_ID, 4, "Great service");

            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(completedBooking));
            when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(false);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            when(reviewRepository.save(captor.capture())).thenAnswer(inv -> {
                Review saved = spy(inv.getArgument(0, Review.class));
                when(saved.getId()).thenReturn(REVIEW_ID);
                return saved;
            });
            // After save, the aggregate recompute re-reads the reviews (includes the new one).
            when(reviewRepository.findByServiceId(SERVICE_ID))
                    .thenReturn(List.of(reviewWithRating(4), reviewWithRating(5))); // avg 4.5
            when(reviewRepository.findByVendorId(VENDOR_ID))
                    .thenReturn(List.of(reviewWithRating(4), reviewWithRating(5)));

            ReviewResponse response = reviewService.createReview(CUSTOMER_ID, request);

            // The persisted review carries the request's rating/comment + the booking's relations.
            Review created = captor.getValue();
            assertThat(created.getRating()).isEqualTo(4);
            assertThat(created.getComment()).isEqualTo("Great service");

            // Denormalized averages updated HALF_UP to 2 decimals.
            assertThat(service.getAverageRating()).isEqualByComparingTo(new BigDecimal("4.50"));
            assertThat(vendor.getRatingAvg()).isEqualByComparingTo(new BigDecimal("4.50"));
            verify(serviceRepository).save(service);
            verify(vendorRepository).save(vendor);

            // Response is enriched with names.
            assertThat(response.id()).isEqualTo(REVIEW_ID);
            assertThat(response.serviceTitle()).isEqualTo("Test Service");
            assertThat(response.customerName()).isEqualTo("Customer");
            assertThat(response.vendorName()).isEqualTo("Vendor Biz");
        }

        @Test
        @DisplayName("booking not found → 404")
        void shouldThrowWhenBookingNotFound() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.createReview(CUSTOMER_ID,
                    new ReviewCreateRequest(BOOKING_ID, 4, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Booking not found");
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("caller is not the booking's customer → 422")
        void shouldThrowWhenCallerIsNotCustomer() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(completedBooking));

            assertThatThrownBy(() -> reviewService.createReview(999L,
                    new ReviewCreateRequest(BOOKING_ID, 4, null)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("your own bookings");
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("booking not COMPLETED → 422")
        void shouldThrowWhenBookingNotCompleted() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBooking));

            assertThatThrownBy(() -> reviewService.createReview(CUSTOMER_ID,
                    new ReviewCreateRequest(BOOKING_ID, 4, null)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("completed booking");
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("booking already reviewed → 422, no duplicate")
        void shouldThrowWhenAlreadyReviewed() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(completedBooking));
            when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(true);

            assertThatThrownBy(() -> reviewService.createReview(CUSTOMER_ID,
                    new ReviewCreateRequest(BOOKING_ID, 4, null)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("already been reviewed");
            verify(reviewRepository, never()).save(any());
        }
    }

    // === read paths =========================================================

    @Nested
    @DisplayName("getServiceReviews / getVendorReviews")
    class ReadPaths {

        @Test
        @DisplayName("service reviews returned newest first")
        void shouldReturnServiceReviewsNewestFirst() {
            Review older = reviewWithRatingAndDate(3, LocalDate.of(2026, 1, 1).atStartOfDay());
            Review newer = reviewWithRatingAndDate(5, LocalDate.of(2026, 6, 1).atStartOfDay());
            when(reviewRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(older, newer));

            List<ReviewResponse> result = reviewService.getServiceReviews(SERVICE_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).rating()).isEqualTo(5); // newest first
            assertThat(result.get(1).rating()).isEqualTo(3);
        }

        @Test
        @DisplayName("empty list when a vendor has no reviews")
        void shouldReturnEmptyForVendorWithNoReviews() {
            when(reviewRepository.findByVendorId(VENDOR_ID)).thenReturn(List.of());

            assertThat(reviewService.getVendorReviews(VENDOR_ID)).isEmpty();
        }
    }

    // === helpers ============================================================

    /** A review on the shared completed booking with the given rating (created now). */
    private Review reviewWithRating(int rating) {
        return new Review(completedBooking, customer, vendor, service, rating, "ok");
    }

    /** A review with an explicit createdAt, for ordering assertions. */
    private Review reviewWithRatingAndDate(int rating, java.time.LocalDateTime createdAt) {
        Review review = spy(new Review(completedBooking, customer, vendor, service, rating, "ok"));
        when(review.getCreatedAt()).thenReturn(createdAt);
        return review;
    }
}
