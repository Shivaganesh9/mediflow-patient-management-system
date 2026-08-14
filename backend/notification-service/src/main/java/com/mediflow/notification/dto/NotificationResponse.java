package com.mediflow.notification.dto;

import com.mediflow.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class NotificationResponse {
    UUID id;
    UUID notificationId;
    UUID recipientId;
    NotificationType notificationType;
    UUID sourceEventId;
    String channel;
    String subject;
    String message;
    Instant readAt;
    Instant createdAt;
    Instant updatedAt;
}
