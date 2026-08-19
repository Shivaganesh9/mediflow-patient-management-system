package com.mediflow.analytics.seed;

import com.mediflow.analytics.entity.AppointmentFact;
import com.mediflow.analytics.entity.InvoiceFact;
import com.mediflow.analytics.entity.MedicalRecordFact;
import com.mediflow.analytics.entity.PaymentFact;
import com.mediflow.analytics.entity.ProcessedEvent;
import com.mediflow.analytics.repository.AppointmentFactRepository;
import com.mediflow.analytics.repository.InvoiceFactRepository;
import com.mediflow.analytics.repository.MedicalRecordFactRepository;
import com.mediflow.analytics.repository.PaymentFactRepository;
import com.mediflow.analytics.repository.ProcessedEventRepository;
import com.mediflow.common.seed.DevSeedData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dev-only seeder (active under the {@code seed} profile) that populates the analytics fact
 * tables and the {@code processed_events} idempotency ledger directly, mirroring what the
 * Kafka consumer would normally build from appointment/billing/medical-record events.
 * Idempotent: a fact is only inserted when its ledger entry is missing.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
public class AnalyticsDevDataSeeder implements ApplicationRunner {

    private final AppointmentFactRepository appointmentFactRepository;
    private final InvoiceFactRepository invoiceFactRepository;
    private final PaymentFactRepository paymentFactRepository;
    private final MedicalRecordFactRepository medicalRecordFactRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        seedAppointmentFacts();
        seedInvoiceFacts();
        seedPaymentFacts();
        seedMedicalRecordFacts();
    }

    private void seedAppointmentFacts() {
        LocalDate today = LocalDate.now();
        LocalDateTime morning = today.atTime(10, 0);
        LocalDateTime afternoon = today.atTime(14, 0);
        LocalDateTime lastWeek = today.minusDays(7).atTime(9, 0);
        LocalDateTime nextWeek = today.plusDays(5).atTime(11, 0);

        createAppointmentFact(DevSeedData.APPOINTMENT_1_ID, DevSeedData.EVENT_APPT_1_ID,
                DevSeedData.PATIENT_USER_ID, DevSeedData.DOCTOR_USER_ID,
                today, morning, "SCHEDULED", "GENERAL");
        createAppointmentFact(DevSeedData.APPOINTMENT_2_ID, DevSeedData.EVENT_APPT_2_ID,
                DevSeedData.PATIENT_USER_ID, DevSeedData.DOCTOR_USER_ID,
                today, afternoon, "CONFIRMED", "FOLLOW_UP");
        createAppointmentFact(DevSeedData.APPOINTMENT_3_ID, DevSeedData.EVENT_APPT_3_ID,
                DevSeedData.SECOND_PATIENT_ID, DevSeedData.DOCTOR_USER_ID,
                today.minusDays(7), lastWeek, "COMPLETED", "CONSULTATION");
        createAppointmentFact(DevSeedData.APPOINTMENT_4_ID, DevSeedData.EVENT_APPT_4_ID,
                DevSeedData.SECOND_PATIENT_ID, DevSeedData.SECOND_DOCTOR_ID,
                today.plusDays(5), nextWeek, "SCHEDULED", "GENERAL");
    }

    private void seedInvoiceFacts() {
        createInvoiceFact(DevSeedData.INVOICE_1_ID, DevSeedData.EVENT_INVOICE_1_ID,
                DevSeedData.PATIENT_USER_ID, DevSeedData.APPOINTMENT_1_ID,
                "INV-SEED-0001", "PAID", "USD",
                new BigDecimal("200.00"), new BigDecimal("10.00"),
                new BigDecimal("0.00"), new BigDecimal("210.00"), new BigDecimal("210.00"));
        createInvoiceFact(DevSeedData.INVOICE_2_ID, DevSeedData.EVENT_INVOICE_2_ID,
                DevSeedData.SECOND_PATIENT_ID, DevSeedData.APPOINTMENT_3_ID,
                "INV-SEED-0002", "PENDING", "USD",
                new BigDecimal("120.00"), new BigDecimal("6.00"),
                new BigDecimal("0.00"), new BigDecimal("126.00"), BigDecimal.ZERO);
    }

    private void seedPaymentFacts() {
        createPaymentFact(DevSeedData.EVENT_PAYMENT_1_ID, DevSeedData.INVOICE_1_ID,
                DevSeedData.PATIENT_USER_ID, new BigDecimal("210.00"), "USD",
                "CARD", "COMPLETED");
    }

    private void seedMedicalRecordFacts() {
        LocalDate today = LocalDate.now();
        createMedicalRecordFact(DevSeedData.MEDICAL_RECORD_1_ID, DevSeedData.EVENT_MEDICAL_RECORD_1_ID,
                DevSeedData.PATIENT_USER_ID, DevSeedData.DOCTOR_USER_ID,
                today.minusDays(2), "CONSULTATION");
        createMedicalRecordFact(DevSeedData.MEDICAL_RECORD_2_ID, DevSeedData.EVENT_MEDICAL_RECORD_2_ID,
                DevSeedData.SECOND_PATIENT_ID, DevSeedData.DOCTOR_USER_ID,
                today.minusDays(7), "FOLLOW_UP");
    }

    private void createAppointmentFact(UUID appointmentId, UUID sourceEventId, UUID patientId,
                                       UUID doctorId, LocalDate appointmentDate,
                                       LocalDateTime startTime, String status, String appointmentType) {
        if (ledgerHas(sourceEventId) || appointmentFactRepository.findByAppointmentId(appointmentId).isPresent()) {
            return;
        }
        AppointmentFact fact = new AppointmentFact();
        fact.setAppointmentId(appointmentId);
        fact.setSourceEventId(sourceEventId);
        fact.setPatientId(patientId);
        fact.setDoctorId(doctorId);
        fact.setAppointmentDate(appointmentDate);
        fact.setStartTime(startTime);
        fact.setStatus(status);
        fact.setAppointmentType(appointmentType);
        appointmentFactRepository.save(fact);
        processedEventRepository.save(new ProcessedEvent(sourceEventId, "APPOINTMENT_CREATED", Instant.now()));
    }

    private void createInvoiceFact(UUID invoiceId, UUID sourceEventId, UUID patientId,
                                   UUID appointmentId, String invoiceNumber, String status,
                                   String currency, BigDecimal subtotal, BigDecimal taxAmount,
                                   BigDecimal discount, BigDecimal totalAmount, BigDecimal paidAmount) {
        if (ledgerHas(sourceEventId) || invoiceFactRepository.findByInvoiceId(invoiceId).isPresent()) {
            return;
        }
        InvoiceFact fact = new InvoiceFact();
        fact.setInvoiceId(invoiceId);
        fact.setSourceEventId(sourceEventId);
        fact.setPatientId(patientId);
        fact.setAppointmentId(appointmentId);
        fact.setInvoiceNumber(invoiceNumber);
        fact.setStatus(status);
        fact.setCurrency(currency);
        fact.setSubtotal(subtotal);
        fact.setTaxAmount(taxAmount);
        fact.setDiscount(discount);
        fact.setTotalAmount(totalAmount);
        fact.setPaidAmount(paidAmount);
        fact.setInvoiceDate(Instant.now());
        invoiceFactRepository.save(fact);
        processedEventRepository.save(new ProcessedEvent(sourceEventId, "INVOICE_GENERATED", Instant.now()));
    }

    private void createPaymentFact(UUID sourceEventId, UUID invoiceId, UUID patientId,
                                   BigDecimal amount, String currency, String method, String status) {
        if (ledgerHas(sourceEventId)) {
            return;
        }
        PaymentFact fact = new PaymentFact();
        fact.setId(sourceEventId);
        fact.setSourceEventId(sourceEventId);
        fact.setInvoiceId(invoiceId);
        fact.setPatientId(patientId);
        fact.setAmount(amount);
        fact.setCurrency(currency);
        fact.setMethod(method);
        fact.setStatus(status);
        fact.setPaidAt(Instant.now());
        paymentFactRepository.save(fact);
        processedEventRepository.save(new ProcessedEvent(sourceEventId, "PAYMENT_CONFIRMED", Instant.now()));
    }

    private void createMedicalRecordFact(UUID recordId, UUID sourceEventId, UUID patientId,
                                         UUID doctorId, LocalDate encounterDate, String recordType) {
        if (ledgerHas(sourceEventId) || medicalRecordFactRepository.findByRecordId(recordId).isPresent()) {
            return;
        }
        MedicalRecordFact fact = new MedicalRecordFact();
        fact.setRecordId(recordId);
        fact.setSourceEventId(sourceEventId);
        fact.setPatientId(patientId);
        fact.setDoctorId(doctorId);
        fact.setEncounterDate(encounterDate);
        fact.setRecordType(recordType);
        medicalRecordFactRepository.save(fact);
        processedEventRepository.save(new ProcessedEvent(sourceEventId, "MEDICAL_RECORD_CREATED", Instant.now()));
    }

    private boolean ledgerHas(UUID sourceEventId) {
        return processedEventRepository.existsById(sourceEventId);
    }
}