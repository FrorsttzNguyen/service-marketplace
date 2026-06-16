package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.domain.vendor.VerificationStatus;
import com.hien.marketplace.infrastructure.persistence.VendorRepository;
import com.hien.marketplace.interfaces.dto.response.VendorAdminResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case for admins to manage vendor verification (Phase 5.5).
 *
 * THE BUG THIS FIXES:
 * Vendors register as {@code PENDING} and {@code VendorServiceManagement.createService} blocks any
 * non-approved vendor (see {@code VendorServiceManagement}: {@code if (!vendor.isApproved())}).
 * The domain already had {@link Vendor#approve()} / {@link Vendor#reject()}, but nothing called them,
 * so a vendor was stuck in PENDING forever. This service is the admin path that calls them.
 *
 * DOMAIN-RICH:
 * Status transition logic lives in {@link Vendor#approve()} / {@link Vendor#reject()}. This service
 * ONLY orchestrates: load → delegate to domain method → save → map to DTO. It deliberately does NOT
 * re-implement any status rule (no {@code vendor.setVerificationStatus(...)} here).
 *
 * IDEMPOTENCY:
 * {@link Vendor#approve()} / {@link Vendor#reject()} unconditionally set the status, so approving an
 * already-approved vendor (or rejecting an already-rejected one) is a no-op success — the admin can
 * retry safely without a conflict. We do not throw on repeat approvals/rejections.
 *
 * TRANSACTION BOUNDARIES:
 * Reads use {@code @Transactional(readOnly = true)} (hints Hibernate/JPA we won't write, lets the
 * driver optimize); writes use {@code @Transactional}, matching the conventions in this codebase.
 */
@Service
@RequiredArgsConstructor
public class AdminVendorService {

    private final VendorRepository vendorRepository;

    /**
     * List vendors, optionally filtered by verification status.
     *
     * @param status   filter; if {@code null} return ALL vendors (admin can browse the full list).
     * @param pageable pagination (page/size come from the controller).
     */
    @Transactional(readOnly = true)
    public Page<VendorAdminResponse> listVendors(VerificationStatus status, Pageable pageable) {
        // status == null → no filter; otherwise filter to one status (PENDING / APPROVED / REJECTED).
        // We branch rather than using a Specification because there are only two cases and it reads clearly.
        Page<Vendor> vendors = (status == null)
                ? vendorRepository.findAll(pageable)
                : vendorRepository.findByVerificationStatus(status, pageable);

        // .map(...) on a Page preserves pagination metadata (totalElements, totalPages, ...).
        return vendors.map(this::toResponse);
    }

    /**
     * Approve a vendor: load, delegate to domain method, save.
     *
     * @throws ResourceNotFoundException if no vendor exists with the given id (→ 404).
     */
    @Transactional
    public VendorAdminResponse approveVendor(Long vendorId) {
        Vendor vendor = loadVendorOrThrow(vendorId);
        vendor.approve();   // domain method owns the status transition
        vendorRepository.save(vendor);
        return toResponse(vendor);
    }

    /**
     * Reject a vendor: symmetric to {@link #approveVendor(Long)}.
     *
     * @throws ResourceNotFoundException if no vendor exists with the given id (→ 404).
     */
    @Transactional
    public VendorAdminResponse rejectVendor(Long vendorId) {
        Vendor vendor = loadVendorOrThrow(vendorId);
        vendor.reject();    // domain method owns the status transition
        vendorRepository.save(vendor);
        return toResponse(vendor);
    }

    /**
     * Load a vendor by id or throw the standard 404 exception.
     * Extracted so both approve/reject share one consistent not-found path.
     */
    private Vendor loadVendorOrThrow(Long vendorId) {
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId));
    }

    /**
     * Map a Vendor entity to the admin response DTO.
     *
     * Built inline (no MapStruct) because it's a single, tiny mapping and the spec allows it —
     * matches how {@code VendorAdminResponse} is a purpose-built admin view, not a full entity mirror.
     * {@code vendor.getUser().getEmail()} is admin-only data (see DTO javadoc).
     */
    private VendorAdminResponse toResponse(Vendor vendor) {
        return new VendorAdminResponse(
                vendor.getId(),
                vendor.getUser().getId(),
                vendor.getBusinessName(),
                vendor.getUser().getEmail(),
                vendor.getVerificationStatus(),
                vendor.getCreatedAt()
        );
    }
}
