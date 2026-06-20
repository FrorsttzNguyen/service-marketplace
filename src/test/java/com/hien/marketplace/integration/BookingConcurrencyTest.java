package com.hien.marketplace.integration;

import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Test cho optimistic locking trên Booking entity.
 *
 * Optimistic locking hoạt động thế nào:
 * 1. Thread A load Booking (version=0)
 * 2. Thread B load Booking (version=0)
 * 3. Thread A UPDATE ... WHERE version=0 → thành công, version thành 1
 * 4. Thread B UPDATE ... WHERE version=0 → THẤT BẠI vì version đã là 1
 * 5. Thread B nhận OptimisticLockException
 *
 * Tại sao cần test này?
 * - Verify @Version annotation hoạt động đúng với JPA provider (Hibernate)
 * - Nếu ai đó xoá @Version, test này sẽ thất bại → cảnh báo sớm
 *
 * Lưu ý kỹ thuật:
 * - @DataJpaTest tự động wrap mỗi test trong một transaction.
 * - Để simulate 2 concurrent transactions, phải tạo EntityManager riêng biệt
 *   từ EntityManagerFactory (không dùng TestEntityManager's built-in transaction).
 */
@DataJpaTest
@ActiveProfiles("test")
// Raw EntityManager commits pollute the database; reset context after this test class
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingConcurrencyTest {

    @Autowired
    private EntityManagerFactory emf;

    /**
     * Simulate optimistic locking conflict bằng 2 EntityManager độc lập.
     * Mỗi EntityManager có transaction riêng → mô phỏng 2 request concurrent.
     */
    @Test
    void shouldThrowOptimisticLockExceptionOnConcurrentUpdate() {
        // 1. Setup: persist booking bằng 1 EM riêng (để tránh conflict với test transaction)
        Long bookingId;
        EntityManager setupEm = emf.createEntityManager();
        setupEm.getTransaction().begin();
        User vendorUser = new User("vendor-" + UUID.randomUUID() + "@test.com", "hash", "Vendor", UserRole.VENDOR);
        setupEm.persist(vendorUser);
        Vendor vendor = new Vendor(vendorUser, "Test Vendor");
        setupEm.persist(vendor);
        ServiceEntity service = new ServiceEntity(vendor, "Test", Money.of(5000), PricingType.FIXED, 60);
        setupEm.persist(service);
        User customer = new User("customer-" + UUID.randomUUID() + "@test.com", "hash", "Customer", UserRole.CUSTOMER);
        setupEm.persist(customer);
        // New constructor: (service, customer, vendor, date, startTime, endTime, subtotal, commission)
        Booking booking = new Booking(service, customer, vendor,
                LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0),
                Money.of(5000), Money.of(500));
        setupEm.persist(booking);
        setupEm.getTransaction().commit();
        bookingId = booking.getId();
        setupEm.close();

        // 2. Simulate 2 concurrent transactions
        // Transaction A: load booking
        EntityManager emA = emf.createEntityManager();
        emA.getTransaction().begin();
        Booking bookingA = emA.find(Booking.class, bookingId);
        assertThat(bookingA.getVersion()).isEqualTo(0L);

        // Transaction B: load cùng booking
        EntityManager emB = emf.createEntityManager();
        emB.getTransaction().begin();
        Booking bookingB = emB.find(Booking.class, bookingId);
        assertThat(bookingB.getVersion()).isEqualTo(0L);

        // 3. Transaction A cập nhật trước → thành công, version tăng lên 1
        bookingA.setNotes("Updated by A");
        emA.getTransaction().commit();
        assertThat(bookingA.getVersion()).isEqualTo(1L);

        // 4. Transaction B cập nhật sau → thất bại vì version đã đổi
        // Hibernate wrap OptimisticLockException trong RollbackException khi commit
        bookingB.setNotes("Updated by B");
        assertThatThrownBy(() -> {
            emB.getTransaction().commit(); // commit triggers flush → version check → exception
        }).isInstanceOf(jakarta.persistence.RollbackException.class)
          .hasCauseInstanceOf(OptimisticLockException.class);

        // 5. Cleanup
        emA.close();
        emB.close();
    }

    /**
     * Verify optimistic locking hoạt động bình thường khi không có conflict.
     * Sequential updates (không concurrent) → tất cả thành công, version tăng dần.
     */
    @Test
    void shouldAllowSequentialUpdatesWithoutConflict() {
        // Setup: persist entities
        Long bookingId;
        Long vendorUserId;
        EntityManager setupEm = emf.createEntityManager();
        setupEm.getTransaction().begin();
        User vendorUser = new User("vendor-" + UUID.randomUUID() + "@test.com", "hash", "Vendor", UserRole.VENDOR);
        setupEm.persist(vendorUser);
        vendorUserId = vendorUser.getId();
        Vendor vendor = new Vendor(vendorUser, "Test Vendor");
        setupEm.persist(vendor);
        ServiceEntity service = new ServiceEntity(vendor, "Test", Money.of(5000), PricingType.FIXED, 60);
        setupEm.persist(service);
        User customer = new User("customer-" + UUID.randomUUID() + "@test.com", "hash", "Customer", UserRole.CUSTOMER);
        setupEm.persist(customer);
        // New constructor: (service, customer, vendor, date, startTime, endTime, subtotal, commission)
        Booking booking = new Booking(service, customer, vendor,
                LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0),
                Money.of(5000), Money.of(500));
        setupEm.persist(booking);
        setupEm.getTransaction().commit();
        bookingId = booking.getId();
        setupEm.close();

        // First update: version 0 → 1
        EntityManager em1 = emf.createEntityManager();
        em1.getTransaction().begin();
        Booking b1 = em1.find(Booking.class, bookingId);
        User vUser = em1.find(User.class, vendorUserId);
        assertThat(b1.getVersion()).isEqualTo(0L);
        b1.confirm(vUser);
        em1.getTransaction().commit();
        assertThat(b1.getVersion()).isEqualTo(1L);
        assertThat(b1.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        em1.close();

        // Second update: version 1 → 2 (CONFIRMED → PAID via system webhook, changedBy null)
        EntityManager em2 = emf.createEntityManager();
        em2.getTransaction().begin();
        Booking b2 = em2.find(Booking.class, bookingId);
        b2.markAsPaid(null);  // Simulates Stripe webhook; new lifecycle requires PAID before IN_PROGRESS
        em2.getTransaction().commit();
        assertThat(b2.getVersion()).isEqualTo(2L);
        assertThat(b2.getStatus()).isEqualTo(BookingStatus.PAID);
        em2.close();

        // Third update: version 2 → 3 (PAID → IN_PROGRESS)
        EntityManager em3 = emf.createEntityManager();
        em3.getTransaction().begin();
        Booking b3 = em3.find(Booking.class, bookingId);
        User vUser3 = em3.find(User.class, vendorUserId);
        b3.start(vUser3);
        em3.getTransaction().commit();
        assertThat(b3.getVersion()).isEqualTo(3L);
        assertThat(b3.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        em3.close();
    }
}
