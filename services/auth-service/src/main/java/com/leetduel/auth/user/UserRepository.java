package com.leetduel.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Login accepts either identifier - one query instead of "try username,
    // then try email" as two round trips.
    Optional<User> findByUsernameOrEmail(String username, String email);
}
