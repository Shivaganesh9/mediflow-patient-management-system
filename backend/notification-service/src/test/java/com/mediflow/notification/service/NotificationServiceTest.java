package com.mediflow.notification.service;

import com.mediflow.notification.dto.CreateNotificationRequest;
import com.mediflow.notification.entity.Notification;
import com.mediflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldCreateNotification() {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setRecipientId(java.util.UUID.randomUUID());
        request.setChannel("EMAIL");
        request.setSubject("Appointment Reminder");
        request.setMessage("Your appointment is tomorrow.");

        Notification savedNotification = new Notification();
        savedNotification.setRecipientId(request.getRecipientId());
        savedNotification.setChannel(request.getChannel());
        savedNotification.setSubject(request.getSubject());
        savedNotification.setMessage(request.getMessage());

        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        Notification result = notificationService.createNotification(request);

        assertNotNull(result);
        assertEquals("EMAIL", result.getChannel());
        verify(notificationRepository).save(any(Notification.class));
    }
}
