package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho User entity.
 *
 * Spring Data JPA tự động implement các method dựa trên tên method.
 * findByEmail = SELECT * FROM users WHERE email = ?
 * Không cần viết SQL — Spring tự generate.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByRole(UserRole role);

    boolean existsByEmail(String email);
}
