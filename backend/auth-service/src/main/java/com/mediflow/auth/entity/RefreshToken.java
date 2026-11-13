package com.mediflow.auth.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * One refresh token in a rotation family.
 *
 * <p>Only a SHA-256 hash of the token is stored, so a database dump cannot be replayed
 * against the token endpoint. Every token issued from one login shares a {@code familyId}
 * (also the JWT {@code sid} claim) which is used for session-wide revocation and for
 * reuse detection.</p>
 */
@Entity
@Access(AccessType.FIELD)
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends BaseEntity {

    /** SHA-256 hex of the raw refresh token; the raw value is never persisted. */
    @Column(name = "token", nullable = false, unique = true, length = 500)
    private String tokenHash;

    /** JWT id ({@code jti}) of the raw refresh token. */
    @Column(nullable = false, unique = true, length = 64)
    private String jti;

    /** Refresh-token family / login session id. */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    /** jti of the token that replaced this one during rotation. */
    @Column(name = "replaced_by_jti", length = 64)
    private String replacedByJti;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 40)
    private String revokeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;
}
