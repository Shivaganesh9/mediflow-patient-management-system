package com.mediflow.notification.controller;

import com.mediflow.common.response.ApiResponse;
import com.mediflow.notification.dto.CreateNotificationRequest;
import com.mediflow.notification.entity.Notification;
import com.mediflow.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Notification> createNotification(@Valid @RequestBody CreateNotificationRequest request) {
        return ApiResponse.success(notificationService.createNotification(request), "Notification created successfully");
    }

    @GetMapping
    public ApiResponse<List<Notification>> getAllNotifications() {
        return ApiResponse.success(notificationService.getAllNotifications(), "Notifications retrieved successfully");
    }

    @GetMapping("/{notificationId}")
    public ApiResponse<Notification> getNotification(@PathVariable UUID notificationId) {
        return ApiResponse.success(notificationService.getNotification(notificationId), "Notification retrieved successfully");
    }

    @PatchMapping("/{notificationId}/send")
    public ApiResponse<Notification> markAsSent(@PathVariable UUID notificationId) {
        return ApiResponse.success(notificationService.markAsSent(notificationId), "Notification marked as sent");
    }
}
