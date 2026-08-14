package com.mediflow.notification.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.notification.entity.Notification;
import com.mediflow.notification.entity.NotificationType;
import com.mediflow.notification.mapper.NotificationDraft;
import com.mediflow.notification.repository.NotificationRepository;
import com.mediflow.notification.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private NotificationService notificationService;

    private NotificationDraft draft() {
        return new NotificationDraft(
                UUID.randomUUID(), UUID.randomUUID(), NotificationType.APPOINTMENT_CREATED,
                "IN_APP", "Appointment scheduled", "Your appointment is scheduled for 2026-08-13T10:00:00.");
    }

    @Test
    void shouldCreateNotificationFromEvent() {
        NotificationDraft draft = draft();
        when(notificationRepository.existsBySourceEventId(draft.sourceEventId())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.createFromEvent(draft);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void shouldSkipDuplicateNotificationBySourceEventId() {
        NotificationDraft draft = draft();
        when(notificationRepository.existsBySourceEventId(draft.sourceEventId())).thenReturn(true);

        notificationService.createFromEvent(draft);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void shouldSkipNullDraft() {
        notificationService.createFromEvent(null);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void shouldSkipDraftMissingRequiredData() {
        NotificationDraft draft = new NotificationDraft(null, null, null, null, null, null);

        notificationService.createFromEvent(draft);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void shouldSwallowDuplicateRejectedByUniqueConstraint() {
        NotificationDraft draft = draft();
        when(notificationRepository.existsBySourceEventId(draft.sourceEventId())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        notificationService.createFromEvent(draft);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void shouldReturnScopedNotificationsForNonAdmin() {
        UUID userId = UUID.randomUUID();
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireCurrentUserId()).thenReturn(userId);

        notificationService.getNotificationsForCurrentUser(Pageable.unpaged());

        verify(notificationRepository).findByRecipientId(eq(userId), any(Pageable.class));
    }

    @Test
    void shouldReturnAllNotificationsForAdmin() {
        when(currentUser.isAdmin()).thenReturn(true);

        notificationService.getNotificationsForCurrentUser(Pageable.unpaged());

        verify(notificationRepository).findAll(any(Pageable.class));
    }

    @Test
    void shouldReturnScopedUnreadCountForNonAdmin() {
        UUID userId = UUID.randomUUID();
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireCurrentUserId()).thenReturn(userId);
        when(notificationRepository.countByRecipientIdAndReadAtIsNull(userId)).thenReturn(2L);

        long count = notificationService.getUnreadCountForCurrentUser();

        assertEquals(2L, count);
    }

    @Test
    void shouldReturnAllUnreadCountForAdmin() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(notificationRepository.countByReadAtIsNull()).thenReturn(5L);

        long count = notificationService.getUnreadCountForCurrentUser();

        assertEquals(5L, count);
    }

    @Test
    void shouldMarkOwnNotificationAsRead() {
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setRecipientId(userId);
        when(notificationRepository.findByNotificationId(any(UUID.class))).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = notificationService.markAsRead(UUID.randomUUID());

        assertNotNull(result.getReadAt());
        verify(notificationRepository).save(notification);
    }

    @Test
    void shouldNotChangeReadAtWhenAlreadyRead() {
        Notification notification = new Notification();
        notification.setRecipientId(UUID.randomUUID());
        notification.setReadAt(java.time.Instant.now());
        when(notificationRepository.findByNotificationId(any(UUID.class))).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = notificationService.markAsRead(UUID.randomUUID());

        assertEquals(notification.getReadAt(), result.getReadAt());
        verify(notificationRepository).save(notification);
    }

    @Test
    void shouldThrowWhenNotificationNotFound() {
        when(notificationRepository.findByNotificationId(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markAsRead(UUID.randomUUID()));
    }

    @Test
    void shouldThrowWhenMarkingAnotherUsersNotification() {
        Notification notification = new Notification();
        notification.setRecipientId(UUID.randomUUID());
        when(notificationRepository.findByNotificationId(any(UUID.class))).thenReturn(Optional.of(notification));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(currentUser).assertCanAccess(notification);
        assertThrows(AccessDeniedException.class,
                () -> notificationService.markAsRead(UUID.randomUUID()));
    }
}
