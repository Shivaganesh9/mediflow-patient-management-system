package com.mediflow.auth.repository;

import com.mediflow.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Row lock used to serialise rotation of one token. Without it, two concurrent
     * refreshes could both observe {@code revoked=false} and mint two independent chains.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.jti = :jti")
    Optional<RefreshToken> findByJtiForUpdate(@Param("jti") String jti);

    Optional<RefreshToken> findFirstByFamilyIdAndRevokedFalseOrderByCreatedAtDesc(UUID familyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true, t.revokedAt = :at, t.revokeReason = :reason "
            + "where t.familyId = :familyId and t.revoked = false")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("at") Instant at, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true, t.revokedAt = :at, t.revokeReason = :reason "
            + "where t.user.id = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("at") Instant at, @Param("reason") String reason);
}
