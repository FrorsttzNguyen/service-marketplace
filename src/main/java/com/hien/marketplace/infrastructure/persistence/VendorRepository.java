package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.domain.vendor.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByUserId(Long userId);

    List<Vendor> findByVerificationStatus(VerificationStatus status);

    boolean existsByUserId(Long userId);
}
