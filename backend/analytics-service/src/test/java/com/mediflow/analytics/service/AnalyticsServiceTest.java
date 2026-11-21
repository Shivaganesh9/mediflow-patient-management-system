package com.mediflow.analytics.service;

import com.mediflow.analytics.dto.AppointmentsResponse;
import com.mediflow.analytics.dto.DashboardSummaryResponse;
import com.mediflow.analytics.dto.RevenueResponse;
import com.mediflow.analytics.entity.AppointmentFact;
import com.mediflow.analytics.entity.InvoiceFact;
import com.mediflow.analytics.entity.PaymentFact;
import com.mediflow.analytics.entity.ProcessedEvent;
import com.mediflow.analytics.mapper.AnalyticsEventMapper;
import com.mediflow.analytics.repository.AppointmentFactRepository;
import com.mediflow.analytics.repository.InvoiceFactRepository;
import com.mediflow.analytics.repository.MedicalRecordFactRepository;
import com.mediflow.analytics.repository.PaymentFactRepository;
import com.mediflow.analytics.repository.ProcessedEventRepository;
import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.medicalrecord.event.MedicalRecordEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AppointmentFactRepository appointmentFactRepository;
    @Mock
    private InvoiceFactRepository invoiceFactRepository;
    @Mock
    private PaymentFactRepository paymentFactRepository;
    @Mock
    private MedicalRecordFactRepository medicalRecordFactRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private AnalyticsEventMapper eventMapper;

    @InjectMocks
    private AnalyticsService analyticsService;

    private AppointmentEvent appointmentEvent(UUID eventId) {
        return AppointmentEvent.builder()
                .eventId(eventId)
                .eventType("APPOINTMENT_CREATED")
                .occurredAt(Instant.now())
                .payload(AppointmentEvent.Payload.builder()
                        .appointmentId(UUID.randomUUID())
                        .patientId(UUID.randomUUID())
                        .startTime(LocalDateTime.of(2026, 8, 14, 10, 0))
                        .status("SCHEDULED")
                        .build())
                .build();
    }

    @Test
    void shouldProcessAppointmentEvent() {
        UUID eventId = UUID.randomUUID();
        AppointmentEvent event = appointmentEvent(eventId);
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(eventMapper.toAppointmentFact(event)).thenReturn(Optional.of(new AppointmentFact()));

        analyticsService.processAppointmentEvent(event);

        verify(appointmentFactRepository).save(any(AppointmentFact.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldSkipDuplicateAppointmentEvent() {
        UUID eventId = UUID.randomUUID();
        AppointmentEvent event = appointmentEvent(eventId);
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        analyticsService.processAppointmentEvent(event);

        verify(appointmentFactRepository, never()).save(any(AppointmentFact.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldSkipNullAppointmentEvent() {
        analyticsService.processAppointmentEvent(null);

        verify(appointmentFactRepository, never()).save(any(AppointmentFact.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldSkipAppointmentEventWithNullEventId() {
        analyticsService.processAppointmentEvent(appointmentEvent(null));

        verify(appointmentFactRepository, never()).save(any(AppointmentFact.class));
    }

    @Test
    void shouldNotMarkProcessedWhenEventCannotBeMapped() {
        UUID eventId = UUID.randomUUID();
        AppointmentEvent event = appointmentEvent(eventId);
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(eventMapper.toAppointmentFact(event)).thenReturn(Optional.empty());

        analyticsService.processAppointmentEvent(event);

        verify(appointmentFactRepository, never()).save(any(AppointmentFact.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldSwallowUniqueConstraintViolationOnAppointment() {
        UUID eventId = UUID.randomUUID();
        AppointmentEvent event = appointmentEvent(eventId);
        AppointmentFact mapped = new AppointmentFact();
        mapped.setAppointmentId(UUID.randomUUID());
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(eventMapper.toAppointmentFact(event)).thenReturn(Optional.of(mapped));
        when(appointmentFactRepository.findByAppointmentId(mapped.getAppointmentId())).thenReturn(Optional.empty());
        when(appointmentFactRepository.save(any(AppointmentFact.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        analyticsService.processAppointmentEvent(event);

        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldUpdateExistingAppointmentFact() {
        UUID eventId = UUID.randomUUID();
        AppointmentEvent event = appointmentEvent(eventId);
        AppointmentFact existing = new AppointmentFact();
        existing.setAppointmentId(UUID.randomUUID());
        existing.setStatus("SCHEDULED");
        AppointmentFact mapped = new AppointmentFact();
        mapped.setAppointmentId(existing.getAppointmentId());
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(eventMapper.toAppointmentFact(event)).thenReturn(Optional.of(mapped));
        when(appointmentFactRepository.findByAppointmentId(existing.getAppointmentId()))
                .thenReturn(Optional.of(existing));

        analyticsService.processAppointmentEvent(event);

        verify(appointmentFactRepository).save(existing);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldProcessInvoiceGeneratedEvent() {
        UUID eventId = UUID.randomUUID();
        BillingEvent event = BillingEvent.builder()
                .eventId(eventId)
                .eventType("INVOICE_GENERATED")
                .payload(BillingEvent.Payload.builder().invoiceId(UUID.randomUUID()).build())
                .build();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(eventMapper.toInvoiceFact(event)).thenReturn(Optional.of(new InvoiceFact()));

        analyticsService.processBillingEvent(event);

        verify(invoiceFactRepository).save(any(InvoiceFact.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldProcessPaymentConfirmedEvent() {
        UUID eventId = UUID.randomUUID();
        BillingEvent event = BillingEvent.builder()
                .eventId(eventId)
                .eventType("PAYMENT_CONFIRMED")
                .payload(BillingEvent.Payload.builder().invoiceId(UUID.randomUUID()).build())
                .build();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(eventMapper.toPaymentFact(event)).thenReturn(Optional.of(new PaymentFact()));

        analyticsService.processBillingEvent(event);

        verify(paymentFactRepository).save(any(PaymentFact.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldIgnoreUnknownBillingEventType() {
        UUID eventId = UUID.randomUUID();
        BillingEvent event = BillingEvent.builder().eventId(eventId).eventType("UNKNOWN").build();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        analyticsService.processBillingEvent(event);

        verify(invoiceFactRepository, never()).save(any(InvoiceFact.class));
        verify(paymentFactRepository, never()).save(any(PaymentFact.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldSkipDuplicateBillingEvent() {
        UUID eventId = UUID.randomUUID();
        BillingEvent event = BillingEvent.builder().eventId(eventId).eventType("INVOICE_GENERATED").build();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        analyticsService.processBillingEvent(event);

        verify(invoiceFactRepository, never()).save(any(InvoiceFact.class));
    }

    @Test
    void shouldProcessMedicalRecordEvent() {
        UUID eventId = UUID.randomUUID();
        MedicalRecordEvent event = MedicalRecordEvent.builder().eventId(eventId).build();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(eventMapper.toMedicalRecordFact(event)).thenReturn(Optional.of(new com.mediflow.analytics.entity.MedicalRecordFact()));

        analyticsService.processMedicalRecordEvent(event);

        verify(medicalRecordFactRepository).save(any(com.mediflow.analytics.entity.MedicalRecordFact.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void shouldBuildDashboardSummary() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        when(appointmentFactRepository.countAppointmentsBetween(from, to)).thenReturn(10L);
        when(appointmentFactRepository.countByStatusBetween(eq(from), eq(to), eq("COMPLETED"))).thenReturn(5L);
        when(appointmentFactRepository.countByStatusBetween(eq(from), eq(to), eq("CANCELLED"))).thenReturn(2L);
        when(appointmentFactRepository.countDistinctPatientsBetween(from, to)).thenReturn(8L);
        when(appointmentFactRepository.countDistinctDoctorsBetween(from, to)).thenReturn(3L);
        when(invoiceFactRepository.sumInvoicedBetween(any(), any())).thenReturn(new BigDecimal("100.00"));
        when(paymentFactRepository.sumCollectedBetween(any(), any())).thenReturn(new BigDecimal("60.00"));
        when(paymentFactRepository.sumRefundedBetween(any(), any())).thenReturn(new BigDecimal("5.00"));

        DashboardSummaryResponse summary = analyticsService.getDashboardSummary(from, to);

        assertEquals(10L, summary.getAppointments());
        assertEquals(5L, summary.getCompletedAppointments());
        assertEquals(2L, summary.getCancelledAppointments());
        assertEquals(new BigDecimal("100.00"), summary.getInvoicedRevenue());
        assertEquals(new BigDecimal("60.00"), summary.getCollectedRevenue());
        assertEquals(new BigDecimal("5.00"), summary.getRefundedRevenue());
        assertEquals(new BigDecimal("40.00"), summary.getOutstandingRevenue());
        assertEquals(8L, summary.getActivePatients());
        assertEquals(3L, summary.getDoctors());
    }

    @Test
    void shouldDefaultToCurrentMonthWhenNoRangeProvided() {
        when(appointmentFactRepository.countAppointmentsBetween(any(), any())).thenReturn(0L);
        when(appointmentFactRepository.countByStatusBetween(any(), any(), eq("COMPLETED"))).thenReturn(0L);
        when(appointmentFactRepository.countByStatusBetween(any(), any(), eq("CANCELLED"))).thenReturn(0L);
        when(appointmentFactRepository.countDistinctPatientsBetween(any(), any())).thenReturn(0L);
        when(appointmentFactRepository.countDistinctDoctorsBetween(any(), any())).thenReturn(0L);
        when(invoiceFactRepository.sumInvoicedBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentFactRepository.sumCollectedBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentFactRepository.sumRefundedBetween(any(), any())).thenReturn(BigDecimal.ZERO);

        DashboardSummaryResponse summary = analyticsService.getDashboardSummary(null, null);

        assertNotNull(summary.getFrom());
        assertNotNull(summary.getTo());
    }

    @Test
    void shouldBuildAppointmentsResponse() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        when(appointmentFactRepository.countAppointmentsBetween(from, to)).thenReturn(3L);
        when(appointmentFactRepository.countByStatusBetween(eq(from), eq(to), eq("COMPLETED"))).thenReturn(1L);
        when(appointmentFactRepository.countByStatusBetween(eq(from), eq(to), eq("CANCELLED"))).thenReturn(1L);
        when(appointmentFactRepository.countByStatusGroupedBetween(from, to))
                .thenReturn(List.of(new Object[]{"COMPLETED", 1L}, new Object[]{"CANCELLED", 1L}));
        when(appointmentFactRepository.dailyCountBetween(from, to))
                .thenReturn(List.<Object[]>of(new Object[]{java.sql.Date.valueOf("2026-08-14"), 2L}));

        AppointmentsResponse response = analyticsService.getAppointments(from, to);

        assertEquals(3L, response.getTotalAppointments());
        assertEquals(1L, response.getCompletedAppointments());
        assertEquals(2, response.getStatusBreakdown().size());
        assertEquals(1, response.getDailyTrend().size());
        assertEquals(LocalDate.of(2026, 8, 14), response.getDailyTrend().get(0).getDate());
    }

    @Test
    void shouldBuildRevenueResponseWithMonthlyTrend() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        when(invoiceFactRepository.sumInvoicedBetween(any(), any())).thenReturn(new BigDecimal("300.00"));
        when(paymentFactRepository.sumCollectedBetween(any(), any())).thenReturn(new BigDecimal("200.00"));
        when(paymentFactRepository.sumRefundedBetween(any(), any())).thenReturn(new BigDecimal("50.00"));
        when(invoiceFactRepository.monthlyInvoicedBetween(any(), any()))
                .thenReturn(List.of(new Object[]{java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T00:00:00Z")), new BigDecimal("100.00")},
                        new Object[]{java.sql.Timestamp.from(java.time.Instant.parse("2026-08-01T00:00:00Z")), new BigDecimal("200.00")}));
        when(paymentFactRepository.monthlyPaymentsBetween(any(), any()))
                .thenReturn(List.of(new Object[]{java.sql.Timestamp.from(java.time.Instant.parse("2026-08-01T00:00:00Z")), "COMPLETED", new BigDecimal("200.00")},
                        new Object[]{java.sql.Timestamp.from(java.time.Instant.parse("2026-08-01T00:00:00Z")), "REFUNDED", new BigDecimal("50.00")}));

        RevenueResponse response = analyticsService.getRevenue(from, to);

        assertEquals(new BigDecimal("300.00"), response.getInvoiced());
        assertEquals(new BigDecimal("200.00"), response.getCollected());
        assertEquals(new BigDecimal("50.00"), response.getRefunded());
        assertEquals(new BigDecimal("100.00"), response.getOutstanding());
        assertEquals(2, response.getMonthlyTrend().size());
        RevenueResponse.MonthlyRevenue august = response.getMonthlyTrend().get(1);
        assertEquals("2026-08", august.getMonth());
        assertEquals(new BigDecimal("200.00"), august.getInvoiced());
        assertEquals(new BigDecimal("200.00"), august.getCollected());
        assertEquals(new BigDecimal("50.00"), august.getRefunded());
    }

    @Test
    void shouldThrowWhenFromAfterTo() {
        LocalDate from = LocalDate.of(2026, 8, 31);
        LocalDate to = LocalDate.of(2026, 8, 1);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> analyticsService.getDashboardSummary(from, to));
    }
}