package com.mediflow.notification.seed;

import com.mediflow.common.seed.DevSeedData;
import com.mediflow.notification.entity.Notification;
import com.mediflow.notification.entity.NotificationType;
import com.mediflow.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Dev-only seeder (active under the {@code seed} profile) that creates three deterministic
 * notifications for the seeded {@code patient} user. Idempotent via the unique
 * {@code sourceEventId}.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
public class NotificationDevDataSeeder implements ApplicationRunner {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        createNotification(DevSeedData.NOTIFICATION_1_ID, DevSeedData.PATIENT_USER_ID,
                DevSeedData.EVENT_APPT_1_ID, NotificationType.APPOINTMENT_CREATED,
                "email", "Appointment confirmed", "Your appointment has been scheduled.");
        createNotification(DevSeedData.NOTIFICATION_2_ID, DevSeedData.PATIENT_USER_ID,
                DevSeedData.EVENT_INVOICE_1_ID, NotificationType.INVOICE_GENERATED,
                "email", "Invoice ready", "A new invoice has been generated for you.");
        createNotification(DevSeedData.NOTIFICATION_3_ID, DevSeedData.PATIENT_USER_ID,
                DevSeedData.EVENT_PAYMENT_1_ID, NotificationType.PAYMENT_CONFIRMED,
                "email", "Payment received", "Your payment has been confirmed.");
    }

    private void createNotification(UUID notificationId, UUID recipientId, UUID sourceEventId,
                                    NotificationType type, String channel, String subject,
                                    String message) {
        if (notificationRepository.existsBySourceEventId(sourceEventId)) {
            return;
        }
        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setNotificationId(notificationId);
        notification.setRecipientId(recipientId);
        notification.setNotificationType(type);
        notification.setSourceEventId(sourceEventId);
        notification.setChannel(channel);
        notification.setSubject(subject);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }
}