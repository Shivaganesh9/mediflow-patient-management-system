package com.mediflow.notification.mapper;

import com.mediflow.notification.dto.NotificationResponse;
import com.mediflow.notification.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .notificationId(notification.getNotificationId())
                .recipientId(notification.getRecipientId())
                .notificationType(notification.getNotificationType())
                .sourceEventId(notification.getSourceEventId())
                .channel(notification.getChannel())
                .subject(notification.getSubject())
                .message(notification.getMessage())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}
