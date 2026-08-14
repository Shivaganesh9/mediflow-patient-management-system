package com.mediflow.notification.controller;

import com.mediflow.common.response.ApiResponse;
import com.mediflow.notification.dto.NotificationResponse;
import com.mediflow.notification.mapper.NotificationMapper;
import com.mediflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    @GetMapping
    public ApiResponse<Page<NotificationResponse>> getNotifications(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<NotificationResponse> notifications = notificationService
                .getNotificationsForCurrentUser(pageable)
                .map(notificationMapper::toResponse);
        return ApiResponse.success(notifications, "Notifications retrieved successfully");
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount() {
        return ApiResponse.success(notificationService.getUnreadCountForCurrentUser(),
                "Unread notification count retrieved successfully");
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markAsRead(@PathVariable UUID notificationId) {
        return ApiResponse.success(
                notificationMapper.toResponse(notificationService.markAsRead(notificationId)),
                "Notification marked as read");
    }
}
