package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.domain.provider.Provider;
import com.hien.marketplace.domain.provider.VerificationStatus;
import com.hien.marketplace.infrastructure.persistence.ProviderRepository;
import com.hien.marketplace.interfaces.dto.response.ProviderAdminResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case for admins to manage provider verification (Phase 5.5).
 *
 * THE BUG THIS FIXES:
 * Providers register as {@code PENDING} and {@code ProviderServiceManagement.createService} blocks any
 * non-approved provider (see {@code ProviderServiceManagement}: {@code if (!provider.isApproved())}).
 * The domain already had {@link Provider#approve()} / {@link Provider#reject()}, but nothing called them,
 * so a provider was stuck in PENDING forever. This service is the admin path that calls them.
 *
 * DOMAIN-RICH:
 * Status transition logic lives in {@link Provider#approve()} / {@link Provider#reject()}. This service
 * ONLY orchestrates: load → delegate to domain method → save → map to DTO. It deliberately does NOT
 * re-implement any status rule (no {@code provider.setVerificationStatus(...)} here).
 *
 * IDEMPOTENCY:
 * {@link Provider#approve()} / {@link Provider#reject()} unconditionally set the status, so approving an
 * already-approved provider (or rejecting an already-rejected one) is a no-op success — the admin can
 * retry safely without a conflict. We do not throw on repeat approvals/rejections.
 *
 * TRANSACTION BOUNDARIES:
 * Reads use {@code @Transactional(readOnly = true)} (hints Hibernate/JPA we won't write, lets the
 * driver optimize); writes use {@code @Transactional}, matching the conventions in this codebase.
 */
@Service
@RequiredArgsConstructor
public class AdminProviderService {

    private final ProviderRepository providerRepository;

    /**
     * List providers, optionally filtered by verification status.
     *
     * @param status   filter; if {@code null} return ALL providers (admin can browse the full list).
     * @param pageable pagination (page/size come from the controller).
     */
    @Transactional(readOnly = true)
    public Page<ProviderAdminResponse> listProviders(VerificationStatus status, Pageable pageable) {
        // status == null → no filter; otherwise filter to one status (PENDING / APPROVED / REJECTED).
        // We branch rather than using a Specification because there are only two cases and it reads clearly.
        Page<Provider> providers = (status == null)
                ? providerRepository.findAll(pageable)
                : providerRepository.findByVerificationStatus(status, pageable);

        // .map(...) on a Page preserves pagination metadata (totalElements, totalPages, ...).
        return providers.map(this::toResponse);
    }

    /**
     * Approve a provider: load, delegate to domain method, save.
     *
     * @throws ResourceNotFoundException if no provider exists with the given id (→ 404).
     */
    @Transactional
    public ProviderAdminResponse approveProvider(Long providerId) {
        Provider provider = loadProviderOrThrow(providerId);
        provider.approve();   // domain method owns the status transition
        providerRepository.save(provider);
        return toResponse(provider);
    }

    /**
     * Reject a provider: symmetric to {@link #approveProvider(Long)}.
     *
     * @throws ResourceNotFoundException if no provider exists with the given id (→ 404).
     */
    @Transactional
    public ProviderAdminResponse rejectProvider(Long providerId) {
        Provider provider = loadProviderOrThrow(providerId);
        provider.reject();    // domain method owns the status transition
        providerRepository.save(provider);
        return toResponse(provider);
    }

    /**
     * Load a provider by id or throw the standard 404 exception.
     * Extracted so both approve/reject share one consistent not-found path.
     */
    private Provider loadProviderOrThrow(Long providerId) {
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", providerId));
    }

    /**
     * Map a Provider entity to the admin response DTO.
     *
     * Built inline (no MapStruct) because it's a single, tiny mapping and the spec allows it —
     * matches how {@code ProviderAdminResponse} is a purpose-built admin view, not a full entity mirror.
     * {@code provider.getUser().getEmail()} is admin-only data (see DTO javadoc).
     */
    private ProviderAdminResponse toResponse(Provider provider) {
        return new ProviderAdminResponse(
                provider.getId(),
                provider.getUser().getId(),
                provider.getBusinessName(),
                provider.getUser().getEmail(),
                provider.getVerificationStatus(),
                provider.getCreatedAt()
        );
    }
}
