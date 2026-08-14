package com.mediflow.notification.mapper;

import com.mediflow.notification.entity.NotificationType;

import java.util.UUID;

public record NotificationDraft(
        UUID sourceEventId,
        UUID recipientId,
        NotificationType notificationType,
        String channel,
        String subject,
        String message) {
}
