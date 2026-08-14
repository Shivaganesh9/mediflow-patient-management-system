package com.mediflow.notification.event;

import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.notification.mapper.NotificationDraft;
import com.mediflow.notification.mapper.NotificationEventMapper;
import com.mediflow.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationEventMapper eventMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationConsumer consumer;

    @Test
    void shouldDispatchAppointmentEvent() {
        AppointmentEvent event = AppointmentEvent.builder().eventId(UUID.randomUUID()).build();
        NotificationDraft draft = draft();
        when(eventMapper.fromAppointmentEvent(event)).thenReturn(Optional.of(draft));

        consumer.consumeAppointmentEvent(event);

        verify(notificationService).createFromEvent(draft);
    }

    @Test
    void shouldIgnoreAppointmentEventWithoutDraft() {
        AppointmentEvent event = AppointmentEvent.builder().eventId(UUID.randomUUID()).build();
        when(eventMapper.fromAppointmentEvent(event)).thenReturn(Optional.empty());

        consumer.consumeAppointmentEvent(event);

        verify(notificationService, never()).createFromEvent(any());
    }

    @Test
    void shouldDispatchBillingEvent() {
        BillingEvent event = BillingEvent.builder().eventId(UUID.randomUUID()).build();
        NotificationDraft draft = draft();
        when(eventMapper.fromBillingEvent(event)).thenReturn(Optional.of(draft));

        consumer.consumeBillingEvent(event);

        verify(notificationService).createFromEvent(draft);
    }

    @Test
    void shouldIgnoreBillingEventWithoutDraft() {
        BillingEvent event = BillingEvent.builder().eventId(UUID.randomUUID()).build();
        when(eventMapper.fromBillingEvent(event)).thenReturn(Optional.empty());

        consumer.consumeBillingEvent(event);

        verify(notificationService, never()).createFromEvent(any());
    }

    private NotificationDraft draft() {
        return new NotificationDraft(
                UUID.randomUUID(), UUID.randomUUID(),
                com.mediflow.notification.entity.NotificationType.APPOINTMENT_CREATED,
                "IN_APP", "Appointment scheduled", "Your appointment is scheduled.");
    }
}
