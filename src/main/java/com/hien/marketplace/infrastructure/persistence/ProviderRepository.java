package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.provider.Provider;
import com.hien.marketplace.domain.provider.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByUserId(Long userId);

    List<Provider> findByVerificationStatus(VerificationStatus status);

    // Phase 5.5: paginated filter for the admin provider-approval dashboard.
    // Spring Data JPA derives the query from the method name — same filter as the List
    // variant above, but returns a Page so the admin UI can paginate by status.
    //
    // WHY @EntityGraph(attributePaths = "user"):
    // Provider.user is @OneToOne(FetchType.LAZY) (see Provider.java). AdminProviderService.toResponse()
    // reads provider.getUser().getId()/getEmail() for every row on the page. Without this graph, listing
    // N providers fires 1 query for the page + N lazy User SELECTs (the classic N+1 problem).
    // The inline attributePaths form eagerly fetches `user` via a single JOIN in the same query.
    @EntityGraph(attributePaths = "user")
    Page<Provider> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    // Override the inherited JpaRepository.findAll(Pageable) so the admin "list all providers" path
    // (status == null branch in AdminProviderService.listProviders) also fetches `user` in one query
    // and avoids the same N+1 as the filtered branch above.
    @Override
    @EntityGraph(attributePaths = "user")
    Page<Provider> findAll(Pageable pageable);

    boolean existsByUserId(Long userId);
}
