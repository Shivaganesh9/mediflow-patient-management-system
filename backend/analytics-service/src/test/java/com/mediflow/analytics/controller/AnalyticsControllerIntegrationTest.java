package com.mediflow.analytics.controller;

import com.mediflow.analytics.AbstractAnalyticsIntegrationTest;
import com.mediflow.analytics.entity.AppointmentFact;
import com.mediflow.analytics.entity.InvoiceFact;
import com.mediflow.analytics.entity.MedicalRecordFact;
import com.mediflow.analytics.entity.PaymentFact;
import com.mediflow.analytics.repository.AppointmentFactRepository;
import com.mediflow.analytics.repository.InvoiceFactRepository;
import com.mediflow.analytics.repository.MedicalRecordFactRepository;
import com.mediflow.analytics.repository.PaymentFactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalyticsControllerIntegrationTest extends AbstractAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentFactRepository appointmentFactRepository;
    @Autowired
    private InvoiceFactRepository invoiceFactRepository;
    @Autowired
    private PaymentFactRepository paymentFactRepository;
    @Autowired
    private MedicalRecordFactRepository medicalRecordFactRepository;

    private void seedFacts() {
        UUID doctorA = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();

        appointmentFactRepository.save(appointmentFact(patientA, doctorA, LocalDate.of(2026, 8, 10), "COMPLETED"));
        appointmentFactRepository.save(appointmentFact(patientA, doctorA, LocalDate.of(2026, 8, 12), "CANCELLED"));
        appointmentFactRepository.save(appointmentFact(patientB, doctorB, LocalDate.of(2026, 8, 20), "COMPLETED"));

        invoiceFactRepository.save(invoiceFact("PENDING", "100.00", Instant.parse("2026-08-05T10:00:00Z")));
        invoiceFactRepository.save(invoiceFact("CANCELLED", "250.00", Instant.parse("2026-08-06T10:00:00Z")));

        paymentFactRepository.save(paymentFact("COMPLETED", "100.00", Instant.parse("2026-08-10T10:00:00Z")));
        paymentFactRepository.save(paymentFact("REFUNDED", "30.00", Instant.parse("2026-08-11T10:00:00Z")));

        medicalRecordFactRepository.save(medicalRecordFact(patientA, LocalDate.of(2026, 8, 10)));
        medicalRecordFactRepository.save(medicalRecordFact(UUID.randomUUID(), LocalDate.of(2026, 8, 15)));
    }

    @Test
    void dashboardSummaryReturnsAggregates() throws Exception {
        seedFacts();
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/analytics/dashboard/summary")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appointments").value(3))
                .andExpect(jsonPath("$.data.completedAppointments").value(2))
                .andExpect(jsonPath("$.data.cancelledAppointments").value(1))
                .andExpect(jsonPath("$.data.activePatients").value(2))
                .andExpect(jsonPath("$.data.doctors").value(2))
                .andExpect(jsonPath("$.data.invoicedRevenue").value(100.0))
                .andExpect(jsonPath("$.data.collectedRevenue").value(100.0))
                .andExpect(jsonPath("$.data.refundedRevenue").value(30.0))
                .andExpect(jsonPath("$.data.outstandingRevenue").value(0.0));
    }

    @Test
    void appointmentsReturnsTrendAndBreakdown() throws Exception {
        seedFacts();
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/analytics/appointments")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAppointments").value(3))
                .andExpect(jsonPath("$.data.statusBreakdown.length()").value(2))
                .andExpect(jsonPath("$.data.dailyTrend.length()").value(3));
    }

    @Test
    void revenueReturnsMonthlyTrend() throws Exception {
        seedFacts();
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/analytics/revenue")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiced").value(100.0))
                .andExpect(jsonPath("$.data.collected").value(100.0))
                .andExpect(jsonPath("$.data.refunded").value(30.0))
                .andExpect(jsonPath("$.data.monthlyTrend.length()").value(1))
                .andExpect(jsonPath("$.data.monthlyTrend[0].month").value("2026-08"));
    }

    @Test
    void doctorsReturnsRanking() throws Exception {
        seedFacts();
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/analytics/doctors")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].appointments").value(2))
                .andExpect(jsonPath("$.data[0].completed").value(1));
    }

    @Test
    void patientsReturnsActivity() throws Exception {
        seedFacts();
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/analytics/patients")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPatients").value(2))
                .andExpect(jsonPath("$.data.newPatients").value(2))
                .andExpect(jsonPath("$.data.mostActive[0].appointments").value(2));
    }

    @Test
    void supportsDateRangeParams() throws Exception {
        seedFacts();
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/analytics/appointments")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-15")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAppointments").value(2));
    }

    @Test
    void doctorTokenCanReadAnalytics() throws Exception {
        seedFacts();
        String doctorToken = token("doctor1", UUID.randomUUID(), List.of("DOCTOR"));

        mockMvc.perform(get("/api/v1/analytics/dashboard/summary")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointments").value(3));
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

    private InvoiceFact invoiceFact(String status, String total, Instant invoiceDate) {
        InvoiceFact fact = new InvoiceFact();
        fact.setInvoiceId(UUID.randomUUID());
        fact.setSourceEventId(UUID.randomUUID());
        fact.setPatientId(UUID.randomUUID());
        fact.setInvoiceNumber("INV-" + UUID.randomUUID());
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