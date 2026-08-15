package com.mediflow.analytics.event;

import com.mediflow.analytics.service.AnalyticsService;
import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.medicalrecord.event.MedicalRecordEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalyticsConsumerTest {

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsConsumer consumer;

    @Test
    void shouldDispatchAppointmentEvent() {
        AppointmentEvent event = AppointmentEvent.builder().eventId(UUID.randomUUID()).build();

        consumer.consumeAppointmentEvent(event);

        verify(analyticsService).processAppointmentEvent(event);
    }

    @Test
    void shouldDispatchNullAppointmentEvent() {
        consumer.consumeAppointmentEvent(null);

        verify(analyticsService).processAppointmentEvent(null);
    }

    @Test
    void shouldDispatchBillingEvent() {
        BillingEvent event = BillingEvent.builder().eventId(UUID.randomUUID()).build();

        consumer.consumeBillingEvent(event);

        verify(analyticsService).processBillingEvent(event);
    }

    @Test
    void shouldDispatchMedicalRecordEvent() {
        MedicalRecordEvent event = MedicalRecordEvent.builder().eventId(UUID.randomUUID()).build();

        consumer.consumeMedicalRecordEvent(event);

        verify(analyticsService).processMedicalRecordEvent(event);
    }

    @Test
    void shouldNeverCallOtherProcessors() {
        consumer.consumeAppointmentEvent(null);

        verify(analyticsService, never()).processBillingEvent(any());
        verify(analyticsService, never()).processMedicalRecordEvent(any());
    }
}