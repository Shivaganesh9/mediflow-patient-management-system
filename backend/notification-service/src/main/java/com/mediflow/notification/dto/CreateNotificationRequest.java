package com.mediflow.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateNotificationRequest {

    @NotNull
    private UUID recipientId;

    @NotBlank
    private String channel;

    @NotBlank
    private String subject;

    @NotBlank
    private String message;
}
