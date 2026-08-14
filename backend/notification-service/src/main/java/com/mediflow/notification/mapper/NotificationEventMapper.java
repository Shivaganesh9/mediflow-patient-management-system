package com.mediflow.notification.mapper;

import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.notification.entity.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NotificationEventMapper {

    private static final String CHANNEL_IN_APP = "IN_APP";

    public Optional<NotificationDraft> fromAppointmentEvent(AppointmentEvent event) {
        if (event == null || event.getPayload() == null || event.getPayload().getPatientId() == null) {
            return Optional.empty();
        }
        NotificationType type = switch (event.getEventType()) {
            case "APPOINTMENT_CREATED" -> NotificationType.APPOINTMENT_CREATED;
            case "APPOINTMENT_UPDATED" -> NotificationType.APPOINTMENT_UPDATED;
            case "APPOINTMENT_CANCELLED" -> NotificationType.APPOINTMENT_CANCELLED;
            default -> null;
        };
        if (type == null) {
            return Optional.empty();
        }
        AppointmentEvent.Payload payload = event.getPayload();
        String subject = switch (type) {
            case APPOINTMENT_CREATED -> "Appointment scheduled";
            case APPOINTMENT_UPDATED -> "Appointment updated";
            case APPOINTMENT_CANCELLED -> "Appointment cancelled";
            default -> "Appointment update";
        };
        String message = switch (type) {
            case APPOINTMENT_CREATED -> "Your appointment is scheduled for " + payload.getStartTime() + ".";
            case APPOINTMENT_UPDATED -> "Your appointment has been rescheduled to " + payload.getStartTime() + ".";
            case APPOINTMENT_CANCELLED -> "Your appointment for " + payload.getStartTime() + " has been cancelled.";
            default -> "There has been an update to your appointment.";
        };
        return Optional.of(new NotificationDraft(
                event.getEventId(), payload.getPatientId(), type, CHANNEL_IN_APP, subject, message));
    }

    public Optional<NotificationDraft> fromBillingEvent(BillingEvent event) {
        if (event == null || event.getPayload() == null || event.getPayload().getPatientId() == null) {
            return Optional.empty();
        }
        NotificationType type = switch (event.getEventType()) {
            case "INVOICE_GENERATED" -> NotificationType.INVOICE_GENERATED;
            case "PAYMENT_CONFIRMED" -> NotificationType.PAYMENT_CONFIRMED;
            default -> null;
        };
        if (type == null) {
            return Optional.empty();
        }
        BillingEvent.Payload payload = event.getPayload();
        String subject = switch (type) {
            case INVOICE_GENERATED -> "Invoice generated";
            case PAYMENT_CONFIRMED -> "Payment confirmed";
            default -> "Billing update";
        };
        String message = switch (type) {
            case INVOICE_GENERATED -> "Invoice " + payload.getInvoiceNumber()
                    + " for " + payload.getTotalAmount() + " " + payload.getCurrency() + " is ready.";
            case PAYMENT_CONFIRMED -> "Your payment of " + payload.getPaymentAmount()
                    + " " + payload.getCurrency() + " was confirmed.";
            default -> "There has been an update to your billing.";
        };
        return Optional.of(new NotificationDraft(
                event.getEventId(), payload.getPatientId(), type, CHANNEL_IN_APP, subject, message));
    }
}
