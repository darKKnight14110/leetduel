package com.leetduel.auth.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHashAndType(String tokenHash, TokenType type);

    // Bulk update, not "load all pending rows then save() each" - this runs
    // on every resend-verification / forgot-password call, and the rows
    // themselves are never read afterward, so there's nothing to justify
    // pulling them into memory first.
    @Modifying
    @Query("update VerificationToken t set t.usedAt = :now "
            + "where t.userId = :userId and t.type = :type and t.usedAt is null")
    void invalidateAllPending(@Param("userId") UUID userId, @Param("type") TokenType type, @Param("now") Instant now);
}
