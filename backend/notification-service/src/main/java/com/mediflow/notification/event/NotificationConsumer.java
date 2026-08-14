package com.mediflow.notification.event;

import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.notification.mapper.NotificationDraft;
import com.mediflow.notification.mapper.NotificationEventMapper;
import com.mediflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    public static final String TOPIC_APPOINTMENT_CREATED = "appointment.created.v1";
    public static final String TOPIC_APPOINTMENT_UPDATED = "appointment.updated.v1";
    public static final String TOPIC_APPOINTMENT_CANCELLED = "appointment.cancelled.v1";
    public static final String TOPIC_INVOICE_GENERATED = "invoice.generated.v1";
    public static final String TOPIC_PAYMENT_CONFIRMED = "payment.confirmed.v1";

    private final NotificationEventMapper eventMapper;
    private final NotificationService notificationService;

    @KafkaListener(topics = {
            TOPIC_APPOINTMENT_CREATED, TOPIC_APPOINTMENT_UPDATED, TOPIC_APPOINTMENT_CANCELLED
    })
    public void consumeAppointmentEvent(AppointmentEvent event) {
        log.debug("Received appointment event {}", event == null ? "null" : event.getEventId());
        dispatch(eventMapper.fromAppointmentEvent(event));
    }

    @KafkaListener(topics = {TOPIC_INVOICE_GENERATED, TOPIC_PAYMENT_CONFIRMED})
    public void consumeBillingEvent(BillingEvent event) {
        log.debug("Received billing event {}", event == null ? "null" : event.getEventId());
        dispatch(eventMapper.fromBillingEvent(event));
    }

    private void dispatch(Optional<NotificationDraft> draft) {
        draft.ifPresent(notificationService::createFromEvent);
    }
}
