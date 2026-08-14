package com.mediflow.notification.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.notification.entity.Notification;
import com.mediflow.notification.mapper.NotificationDraft;
import com.mediflow.notification.repository.NotificationRepository;
import com.mediflow.notification.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUser currentUser;

    @Transactional
    public void createFromEvent(NotificationDraft draft) {
        if (draft == null || draft.sourceEventId() == null || draft.recipientId() == null) {
            log.warn("Skipping notification draft with missing required data");
            return;
        }
        if (notificationRepository.existsBySourceEventId(draft.sourceEventId())) {
            log.info("Skipping duplicate notification for source event {}", draft.sourceEventId());
            return;
        }

        Notification notification = new Notification();
        notification.setRecipientId(draft.recipientId());
        notification.setNotificationType(draft.notificationType());
        notification.setSourceEventId(draft.sourceEventId());
        notification.setChannel(draft.channel());
        notification.setSubject(draft.subject());
        notification.setMessage(draft.message());
        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate notification for source event {} rejected by unique constraint",
                    draft.sourceEventId());
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> getNotificationsForCurrentUser(Pageable pageable) {
        if (currentUser.isAdmin()) {
            return notificationRepository.findAll(pageable);
        }
        UUID userId = currentUser.requireCurrentUserId();
        return notificationRepository.findByRecipientId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForCurrentUser() {
        if (currentUser.isAdmin()) {
            return notificationRepository.countByReadAtIsNull();
        }
        UUID userId = currentUser.requireCurrentUserId();
        return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Notification markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findByNotificationId(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));
        currentUser.assertCanAccess(notification);
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return notificationRepository.save(notification);
    }
}
