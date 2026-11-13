package com.mediflow.auth.repository;

import com.mediflow.auth.entity.AuthAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEvent, UUID> {
}
