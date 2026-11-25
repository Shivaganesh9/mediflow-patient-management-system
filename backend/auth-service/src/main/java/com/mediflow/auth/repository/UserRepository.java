package com.mediflow.auth.repository;

import com.mediflow.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("select u from User u where u.username = :value or u.email = :value")
    Optional<User> findByUsernameOrEmail(@Param("value") String value);

    /** Serialises concurrent failed-login / lockout bookkeeping for one account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Whether any account currently holds the given role. Used by the one-time administrator
     * bootstrap to prove it will not overwrite an existing administrator.
     */
    @Query("select count(u) > 0 from User u join u.roles r where r.name = :roleName")
    boolean existsByRoleName(@Param("roleName") String roleName);
}
