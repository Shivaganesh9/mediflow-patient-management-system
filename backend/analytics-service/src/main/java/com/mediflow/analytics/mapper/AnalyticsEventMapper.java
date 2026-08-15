package com.mediflow.analytics.mapper;

import com.mediflow.analytics.entity.AppointmentFact;
import com.mediflow.analytics.entity.InvoiceFact;
import com.mediflow.analytics.entity.MedicalRecordFact;
import com.mediflow.analytics.entity.PaymentFact;
import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.medicalrecord.event.MedicalRecordEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AnalyticsEventMapper {

    public Optional<AppointmentFact> toAppointmentFact(AppointmentEvent event) {
        if (event == null || event.getEventId() == null || event.getPayload() == null
                || event.getPayload().getAppointmentId() == null) {
            return Optional.empty();
        }
        AppointmentEvent.Payload payload = event.getPayload();
        AppointmentFact fact = new AppointmentFact();
        fact.setAppointmentId(payload.getAppointmentId());
        fact.setSourceEventId(event.getEventId());
        fact.setPatientId(payload.getPatientId());
        fact.setDoctorId(payload.getDoctorId());
        if (payload.getStartTime() != null) {
            fact.setStartTime(payload.getStartTime());
            fact.setAppointmentDate(payload.getStartTime().toLocalDate());
        }
        fact.setStatus(payload.getStatus());
        fact.setAppointmentType(payload.getAppointmentType());
        fact.setVersion(payload.getVersion());
        return Optional.of(fact);
    }

    public Optional<InvoiceFact> toInvoiceFact(BillingEvent event) {
        if (event == null || event.getEventId() == null || event.getPayload() == null
                || event.getPayload().getInvoiceId() == null) {
            return Optional.empty();
        }
        BillingEvent.Payload payload = event.getPayload();
        InvoiceFact fact = new InvoiceFact();
        fact.setInvoiceId(payload.getInvoiceId());
        fact.setSourceEventId(event.getEventId());
        fact.setPatientId(payload.getPatientId());
        fact.setAppointmentId(payload.getAppointmentId());
        fact.setInvoiceNumber(payload.getInvoiceNumber());
        if (payload.getStatus() != null) {
            fact.setStatus(payload.getStatus().name());
        }
        fact.setCurrency(payload.getCurrency());
        fact.setSubtotal(payload.getSubtotal());
        fact.setTaxAmount(payload.getTaxAmount());
        fact.setDiscount(payload.getDiscount());
        fact.setTotalAmount(payload.getTotalAmount());
        fact.setPaidAmount(payload.getPaidAmount());
        fact.setInvoiceDate(event.getOccurredAt());
        fact.setVersion(payload.getVersion());
        return Optional.of(fact);
    }

    public Optional<PaymentFact> toPaymentFact(BillingEvent event) {
        if (event == null || event.getEventId() == null || event.getPayload() == null
                || event.getPayload().getInvoiceId() == null) {
            return Optional.empty();
        }
        BillingEvent.Payload payload = event.getPayload();
        PaymentFact fact = new PaymentFact();
        fact.setSourceEventId(event.getEventId());
        fact.setInvoiceId(payload.getInvoiceId());
        fact.setPatientId(payload.getPatientId());
        fact.setAmount(payload.getPaymentAmount());
        fact.setCurrency(payload.getCurrency());
        if (payload.getPaymentMethod() != null) {
            fact.setMethod(payload.getPaymentMethod().name());
        }
        if (payload.getPaymentStatus() != null) {
            fact.setStatus(payload.getPaymentStatus().name());
        }
        fact.setPaidAt(event.getOccurredAt());
        return Optional.of(fact);
    }

    public Optional<MedicalRecordFact> toMedicalRecordFact(MedicalRecordEvent event) {
        if (event == null || event.getEventId() == null || event.getPayload() == null
                || event.getPayload().getRecordId() == null) {
            return Optional.empty();
        }
        MedicalRecordEvent.Payload payload = event.getPayload();
        MedicalRecordFact fact = new MedicalRecordFact();
        fact.setRecordId(payload.getRecordId());
        fact.setSourceEventId(event.getEventId());
        fact.setPatientId(payload.getPatientId());
        fact.setDoctorId(payload.getDoctorId());
        fact.setEncounterDate(payload.getEncounterDate());
        if (payload.getRecordType() != null) {
            fact.setRecordType(payload.getRecordType().name());
        }
        return Optional.of(fact);
    }
}