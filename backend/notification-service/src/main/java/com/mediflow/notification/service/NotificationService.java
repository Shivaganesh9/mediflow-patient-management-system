package com.mediflow.notification.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.notification.dto.CreateNotificationRequest;
import com.mediflow.notification.entity.Notification;
import com.mediflow.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification createNotification(CreateNotificationRequest request) {
        Notification notification = new Notification();
        notification.setRecipientId(request.getRecipientId());
        notification.setChannel(request.getChannel());
        notification.setSubject(request.getSubject());
        notification.setMessage(request.getMessage());
        return notificationRepository.save(notification);
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    public Notification getNotification(UUID notificationId) {
        return notificationRepository.findByNotificationId(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));
    }

    @Transactional
    public Notification markAsSent(UUID notificationId) {
        Notification notification = getNotification(notificationId);
        notification.setStatus("SENT");
        return notificationRepository.save(notification);
    }
}
