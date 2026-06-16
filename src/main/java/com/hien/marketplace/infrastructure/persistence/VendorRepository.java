package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.domain.vendor.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByUserId(Long userId);

    List<Vendor> findByVerificationStatus(VerificationStatus status);

    // Phase 5.5: paginated filter for the admin vendor-approval dashboard.
    // Spring Data JPA derives the query from the method name — same filter as the List
    // variant above, but returns a Page so the admin UI can paginate by status.
    Page<Vendor> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    boolean existsByUserId(Long userId);
}
