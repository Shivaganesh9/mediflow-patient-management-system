package com.mediflow.notification.seed;

import com.mediflow.common.seed.DevSeedData;
import com.mediflow.notification.entity.Notification;
import com.mediflow.notification.entity.NotificationType;
import com.mediflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDevDataSeederTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationDevDataSeeder seeder;

    @Test
    void seedCreatesThreeNotificationsForPatient() {
        when(notificationRepository.existsBySourceEventId(any())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(3)).save(captor.capture());

        Set<java.util.UUID> ids = captor.getAllValues().stream()
                .map(Notification::getNotificationId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(DevSeedData.NOTIFICATION_1_ID, DevSeedData.NOTIFICATION_2_ID,
                DevSeedData.NOTIFICATION_3_ID), ids);

        assertTrue(captor.getAllValues().stream()
                .allMatch(n -> n.getRecipientId().equals(DevSeedData.PATIENT_USER_ID)));
        assertTrue(captor.getAllValues().stream()
                .anyMatch(n -> n.getNotificationType() == NotificationType.APPOINTMENT_CREATED));
        assertTrue(captor.getAllValues().stream()
                .anyMatch(n -> n.getNotificationType() == NotificationType.INVOICE_GENERATED));
        assertTrue(captor.getAllValues().stream()
                .anyMatch(n -> n.getNotificationType() == NotificationType.PAYMENT_CONFIRMED));
    }

    @Test
    void seedIsIdempotentWhenSourceEventsExist() {
        when(notificationRepository.existsBySourceEventId(any())).thenReturn(true);

        seeder.seed();
        seeder.seed();

        verify(notificationRepository, never()).save(any(Notification.class));
    }
}