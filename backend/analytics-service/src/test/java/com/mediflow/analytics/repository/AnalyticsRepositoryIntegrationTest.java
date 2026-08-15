package com.mediflow.analytics.repository;

import com.mediflow.analytics.AbstractAnalyticsIntegrationTest;
import com.mediflow.analytics.entity.AppointmentFact;
import com.mediflow.analytics.entity.InvoiceFact;
import com.mediflow.analytics.entity.MedicalRecordFact;
import com.mediflow.analytics.entity.PaymentFact;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class AnalyticsRepositoryIntegrationTest extends AbstractAnalyticsIntegrationTest {

    @Autowired
    private AppointmentFactRepository appointmentFactRepository;
    @Autowired
    private InvoiceFactRepository invoiceFactRepository;
    @Autowired
    private PaymentFactRepository paymentFactRepository;
    @Autowired
    private MedicalRecordFactRepository medicalRecordFactRepository;

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);

    private void seedFacts() {
        UUID doctorA = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();

        appointmentFactRepository.save(appointmentFact(patientA, doctorA, LocalDate.of(2026, 8, 10), "COMPLETED"));
        appointmentFactRepository.save(appointmentFact(patientA, doctorA, LocalDate.of(2026, 8, 12), "CANCELLED"));
        appointmentFactRepository.save(appointmentFact(patientB, doctorB, LocalDate.of(2026, 8, 20), "COMPLETED"));

        invoiceFactRepository.save(invoiceFact("INV-1", "PENDING", "100.00", Instant.parse("2026-08-05T10:00:00Z")));
        invoiceFactRepository.save(invoiceFact("INV-2", "CANCELLED", "250.00", Instant.parse("2026-08-06T10:00:00Z")));

        paymentFactRepository.save(paymentFact("COMPLETED", "100.00", Instant.parse("2026-08-10T10:00:00Z")));
        paymentFactRepository.save(paymentFact("REFUNDED", "30.00", Instant.parse("2026-08-11T10:00:00Z")));

        medicalRecordFactRepository.save(medicalRecordFact(patientA, LocalDate.of(2026, 8, 10)));
        medicalRecordFactRepository.save(medicalRecordFact(UUID.randomUUID(), LocalDate.of(2026, 8, 15)));
        medicalRecordFactRepository.save(medicalRecordFact(UUID.randomUUID(), LocalDate.of(2026, 7, 15)));
    }

    @Test
    void shouldCountAppointmentsByDateAndStatus() {
        seedFacts();

        assertEquals(3L, appointmentFactRepository.countAppointmentsBetween(FROM, TO));
        assertEquals(2L, appointmentFactRepository.countByStatusBetween(FROM, TO, "COMPLETED"));
        assertEquals(1L, appointmentFactRepository.countByStatusBetween(FROM, TO, "CANCELLED"));
        assertEquals(2L, appointmentFactRepository.countDistinctPatientsBetween(FROM, TO));
        assertEquals(2L, appointmentFactRepository.countDistinctDoctorsBetween(FROM, TO));

        List<Object[]> daily = appointmentFactRepository.dailyCountBetween(FROM, TO);
        assertEquals(3, daily.size());
        assertEquals(LocalDate.of(2026, 8, 12), ((java.sql.Date) daily.get(1)[0]).toLocalDate());
        assertEquals(1L, ((Number) daily.get(1)[1]).longValue());

        List<Object[]> statusBreakdown = appointmentFactRepository.countByStatusGroupedBetween(FROM, TO);
        assertEquals(2, statusBreakdown.size());
    }

    @Test
    void shouldRankDoctorsByAppointmentVolume() {
        UUID doctorA = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        appointmentFactRepository.save(appointmentFact(patientA, doctorA, LocalDate.of(2026, 8, 10), "COMPLETED"));
        appointmentFactRepository.save(appointmentFact(patientA, doctorA, LocalDate.of(2026, 8, 12), "COMPLETED"));
        appointmentFactRepository.save(appointmentFact(patientB, doctorB, LocalDate.of(2026, 8, 20), "COMPLETED"));

        List<Object[]> ranking = appointmentFactRepository.doctorRankingBetween(FROM, TO);

        assertEquals(2, ranking.size());
        assertEquals(doctorA, ranking.get(0)[0]);
        assertEquals(2L, ((Number) ranking.get(0)[1]).longValue());
        assertEquals(2L, ((Number) ranking.get(0)[2]).longValue());
        assertEquals(1L, ((Number) ranking.get(1)[1]).longValue());
    }

    @Test
    void shouldRankMostActivePatients() {
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        appointmentFactRepository.save(appointmentFact(patientA, doctor, LocalDate.of(2026, 8, 10), "COMPLETED"));
        appointmentFactRepository.save(appointmentFact(patientA, doctor, LocalDate.of(2026, 8, 12), "COMPLETED"));
        appointmentFactRepository.save(appointmentFact(patientB, doctor, LocalDate.of(2026, 8, 20), "COMPLETED"));

        List<Object[]> mostActive = appointmentFactRepository.mostActivePatientsBetween(FROM, TO, 10);

        assertEquals(2, mostActive.size());
        assertEquals(patientA, mostActive.get(0)[0]);
        assertEquals(2L, ((Number) mostActive.get(0)[1]).longValue());
        assertEquals(LocalDate.of(2026, 8, 10), ((java.sql.Date) mostActive.get(0)[2]).toLocalDate());
    }

    @Test
    void shouldCalculateRevenueExcludingCancelledInvoices() {
        seedFacts();
        Instant fromStart = FROM.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant toExclusive = TO.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        assertEquals(new BigDecimal("100.00"), invoiceFactRepository.sumInvoicedBetween(fromStart, toExclusive));
        assertEquals(new BigDecimal("100.00"), paymentFactRepository.sumCollectedBetween(fromStart, toExclusive));
        assertEquals(new BigDecimal("30.00"), paymentFactRepository.sumRefundedBetween(fromStart, toExclusive));

        List<Object[]> monthly = invoiceFactRepository.monthlyInvoicedBetween(fromStart, toExclusive);
        assertEquals(1, monthly.size());
        assertEquals(new BigDecimal("100.00"), monthly.get(0)[1]);
    }

    @Test
    void shouldCountNewPatientsByFirstEncounter() {
        seedFacts();

        assertEquals(2L, medicalRecordFactRepository.countNewPatientsBetween(FROM, TO));
    }

    private AppointmentFact appointmentFact(UUID patientId, UUID doctorId, LocalDate date, String status) {
        AppointmentFact fact = new AppointmentFact();
        fact.setAppointmentId(UUID.randomUUID());
        fact.setSourceEventId(UUID.randomUUID());
        fact.setPatientId(patientId);
        fact.setDoctorId(doctorId);
        fact.setAppointmentDate(date);
        fact.setStartTime(LocalDateTime.of(date, LocalDateTime.of(2026, 8, 14, 10, 0).toLocalTime()));
        fact.setStatus(status);
        fact.setAppointmentType("CONSULTATION");
        fact.setVersion(1L);
        return fact;
    }

    private InvoiceFact invoiceFact(String number, String status, String total, Instant invoiceDate) {
        InvoiceFact fact = new InvoiceFact();
        fact.setInvoiceId(UUID.randomUUID());
        fact.setSourceEventId(UUID.randomUUID());
        fact.setPatientId(UUID.randomUUID());
        fact.setInvoiceNumber(number);
        fact.setStatus(status);
        fact.setCurrency("USD");
        fact.setTotalAmount(new BigDecimal(total));
        fact.setInvoiceDate(invoiceDate);
        fact.setVersion(1L);
        return fact;
    }

    private PaymentFact paymentFact(String status, String amount, Instant paidAt) {
        PaymentFact fact = new PaymentFact();
        fact.setSourceEventId(UUID.randomUUID());
        fact.setInvoiceId(UUID.randomUUID());
        fact.setPatientId(UUID.randomUUID());
        fact.setAmount(new BigDecimal(amount));
        fact.setCurrency("USD");
        fact.setMethod("CARD");
        fact.setStatus(status);
        fact.setPaidAt(paidAt);
        return fact;
    }

    private MedicalRecordFact medicalRecordFact(UUID patientId, LocalDate encounterDate) {
        MedicalRecordFact fact = new MedicalRecordFact();
        fact.setRecordId(UUID.randomUUID());
        fact.setSourceEventId(UUID.randomUUID());
        fact.setPatientId(patientId);
        fact.setDoctorId(UUID.randomUUID());
        fact.setEncounterDate(encounterDate);
        fact.setRecordType("CONSULTATION");
        return fact;
    }
}