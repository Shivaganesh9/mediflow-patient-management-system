package com.mediflow.notification.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID notificationId = UUID.randomUUID();

    @Column(nullable = false)
    private UUID recipientId;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String message;

    @Column(nullable = false)
    private String status = "PENDING";
}
