package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.domain.vendor.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByUserId(Long userId);

    List<Vendor> findByVerificationStatus(VerificationStatus status);

    // Phase 5.5: paginated filter for the admin vendor-approval dashboard.
    // Spring Data JPA derives the query from the method name — same filter as the List
    // variant above, but returns a Page so the admin UI can paginate by status.
    //
    // WHY @EntityGraph(attributePaths = "user"):
    // Vendor.user is @OneToOne(FetchType.LAZY) (see Vendor.java). AdminVendorService.toResponse()
    // reads vendor.getUser().getId()/getEmail() for every row on the page. Without this graph, listing
    // N vendors fires 1 query for the page + N lazy User SELECTs (the classic N+1 problem).
    // The inline attributePaths form eagerly fetches `user` via a single JOIN in the same query.
    @EntityGraph(attributePaths = "user")
    Page<Vendor> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    // Override the inherited JpaRepository.findAll(Pageable) so the admin "list all vendors" path
    // (status == null branch in AdminVendorService.listVendors) also fetches `user` in one query
    // and avoids the same N+1 as the filtered branch above.
    @Override
    @EntityGraph(attributePaths = "user")
    Page<Vendor> findAll(Pageable pageable);

    boolean existsByUserId(Long userId);
}
