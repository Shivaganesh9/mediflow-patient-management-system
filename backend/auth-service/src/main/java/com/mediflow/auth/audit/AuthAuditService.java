package com.mediflow.auth.audit;

import com.mediflow.auth.entity.AuthAuditEvent;
import com.mediflow.auth.repository.AuthAuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Writes security audit events in an isolated {@code REQUIRES_NEW} transaction so that
 * (a) an audit row survives a rolled-back login attempt and (b) an audit failure can never
 * mark the caller's transaction rollback-only or turn a successful login into a 500.
 */
@Service
@RequiredArgsConstructor
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);

    private final AuthAuditEventRepository repository;
    private final PlatformTransactionManager transactionManager;

    public void record(String eventType, String outcome, UUID userId, String username, String detail) {
        try {
            RequestMetadata metadata = RequestMetadata.current();
            AuthAuditEvent event = AuthAuditEvent.builder()
                    .userId(userId)
                    .username(username)
                    .eventType(eventType)
                    .outcome(outcome)
                    .ipAddress(metadata.ipAddress())
                    .userAgent(metadata.userAgent())
                    .detail(detail == null ? null : truncate(detail))
                    .build();

            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> repository.save(event));
        } catch (RuntimeException ex) {
            log.error("Unable to persist auth audit event {} for user {}", eventType, username, ex);
        }
    }

    private static String truncate(String detail) {
        return detail.length() <= 500 ? detail : detail.substring(0, 500);
    }
}
