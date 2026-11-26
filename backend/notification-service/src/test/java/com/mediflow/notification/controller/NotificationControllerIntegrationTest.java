package com.mediflow.notification.controller;

import com.mediflow.notification.AbstractNotificationIntegrationTest;
import com.mediflow.notification.entity.Notification;
import com.mediflow.notification.entity.NotificationType;
import com.mediflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerIntegrationTest extends AbstractNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void isolateFromCommittedRows() {
        // The idempotency suite shares this PostgreSQL container and commits notifications, while the
        // admin-wide assertions below count every row. Deleting inside the test transaction keeps the
        // counts deterministic; the rollback leaves the other suite's committed data untouched.
        notificationRepository.deleteAll();
    }

    @Test
    void listNotificationsScopedToPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        seedNotification(patientId, NotificationType.APPOINTMENT_CREATED);
        seedNotification(patientId, NotificationType.PAYMENT_CONFIRMED);
        seedNotification(UUID.randomUUID(), NotificationType.INVOICE_GENERATED);
        String patientToken = token("patient1", patientId, List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].recipientId").value(patientId.toString()));
    }

    @Test
    void listNotificationsNewestFirst() throws Exception {
        UUID patientId = UUID.randomUUID();
        seedNotification(patientId, NotificationType.APPOINTMENT_CREATED);
        String patientToken = token("patient1", patientId, List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].notificationType").value("APPOINTMENT_CREATED"));
    }

    @Test
    void listNotificationsForAdminReturnsAll() throws Exception {
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        seedNotification(patientA, NotificationType.APPOINTMENT_CREATED);
        seedNotification(patientB, NotificationType.INVOICE_GENERATED);
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void unreadCountScopedToPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String patientToken = token("patient1", patientId, List.of("PATIENT"));

        String notificationId = seedNotification(patientId, NotificationType.APPOINTMENT_CREATED);
        seedNotification(patientId, NotificationType.PAYMENT_CONFIRMED);

        markRead(notificationId, patientToken);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void unreadCountForAdminCountsAll() throws Exception {
        seedNotification(UUID.randomUUID(), NotificationType.APPOINTMENT_CREATED);
        seedNotification(UUID.randomUUID(), NotificationType.INVOICE_GENERATED);
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void markOwnNotificationAsRead() throws Exception {
        UUID patientId = UUID.randomUUID();
        String patientToken = token("patient1", patientId, List.of("PATIENT"));
        String notificationId = seedNotification(patientId, NotificationType.APPOINTMENT_CREATED);

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(notificationId))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
    }

    @Test
    void markOtherPatientsNotificationDenied() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String notificationId = seedNotification(ownerId, NotificationType.APPOINTMENT_CREATED);
        String otherToken = token("patient2", UUID.randomUUID(), List.of("PATIENT"));

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAnyNotificationAsReadByAdmin() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String notificationId = seedNotification(ownerId, NotificationType.INVOICE_GENERATED);
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
    }

    @Test
    void markMissingNotificationReturnsNotFound() throws Exception {
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(patch("/api/v1/notifications/" + UUID.randomUUID() + "/read")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private String seedNotification(UUID recipientId, NotificationType type) {
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setNotificationType(type);
        notification.setSourceEventId(UUID.randomUUID());
        notification.setChannel("IN_APP");
        notification.setSubject("Subject for " + type);
        notification.setMessage("Message body for " + type);
        notificationRepository.save(notification);
        return notification.getNotificationId().toString();
    }

    private void markRead(String notificationId, String token) throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
