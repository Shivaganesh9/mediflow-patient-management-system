package com.mediflow.analytics.service;

import com.mediflow.analytics.dto.AppointmentsResponse;
import com.mediflow.analytics.dto.DashboardSummaryResponse;
import com.mediflow.analytics.dto.DoctorStatResponse;
import com.mediflow.analytics.dto.PatientsResponse;
import com.mediflow.analytics.dto.RevenueResponse;
import com.mediflow.analytics.entity.AppointmentFact;
import com.mediflow.analytics.entity.InvoiceFact;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private static final int MOST_ACTIVE_LIMIT = 10;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_REFUNDED = "REFUNDED";

    private final AppointmentFactRepository appointmentFactRepository;
    private final InvoiceFactRepository invoiceFactRepository;
    private final PaymentFactRepository paymentFactRepository;
    private final MedicalRecordFactRepository medicalRecordFactRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final AnalyticsEventMapper eventMapper;

    // ---------- event processing ----------

    @Transactional
    public void processAppointmentEvent(AppointmentEvent event) {
        if (event == null || event.getEventId() == null) {
            log.warn("Skipping appointment event with missing event id");
            return;
        }
        if (isAlreadyProcessed(event.getEventId())) {
            log.info("Skipping duplicate appointment event {}", event.getEventId());
            return;
        }
        try {
            eventMapper.toAppointmentFact(event).ifPresent(fact -> {
                upsertAppointmentFact(fact);
                markProcessed(event.getEventId(), event.getEventType());
            });
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate appointment event {} rejected by unique constraint", event.getEventId());
        }
    }

    @Transactional
    public void processBillingEvent(BillingEvent event) {
        if (event == null || event.getEventId() == null) {
            log.warn("Skipping billing event with missing event id");
            return;
        }
        if (isAlreadyProcessed(event.getEventId())) {
            log.info("Skipping duplicate billing event {}", event.getEventId());
            return;
        }
        try {
            switch (event.getEventType()) {
                case "INVOICE_GENERATED", "INVOICE_CANCELLED" -> eventMapper.toInvoiceFact(event).ifPresent(fact -> {
                    upsertInvoiceFact(fact);
                    markProcessed(event.getEventId(), event.getEventType());
                });
                case "PAYMENT_CONFIRMED", "PAYMENT_REFUNDED" -> eventMapper.toPaymentFact(event).ifPresent(fact -> {
                    paymentFactRepository.save(fact);
                    markProcessed(event.getEventId(), event.getEventType());
                });
                default -> log.debug("Ignoring unknown billing event type {}", event.getEventType());
            }
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate billing event {} rejected by unique constraint", event.getEventId());
        }
    }

    @Transactional
    public void processMedicalRecordEvent(MedicalRecordEvent event) {
        if (event == null || event.getEventId() == null) {
            log.warn("Skipping medical record event with missing event id");
            return;
        }
        if (isAlreadyProcessed(event.getEventId())) {
            log.info("Skipping duplicate medical record event {}", event.getEventId());
            return;
        }
        try {
            eventMapper.toMedicalRecordFact(event).ifPresent(fact -> {
                medicalRecordFactRepository.save(fact);
                markProcessed(event.getEventId(), event.getEventType());
            });
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate medical record event {} rejected by unique constraint", event.getEventId());
        }
    }

    // ---------- read model queries ----------

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(LocalDate from, LocalDate to) {
        LocalDate f = resolveFrom(from, to);
        LocalDate t = resolveTo(to);
        Instant fromStart = startOfDay(f);
        Instant toExclusive = startOfNextDay(t);

        BigDecimal invoiced = invoiceFactRepository.sumInvoicedBetween(fromStart, toExclusive);
        BigDecimal collected = paymentFactRepository.sumCollectedBetween(fromStart, toExclusive);
        BigDecimal refunded = paymentFactRepository.sumRefundedBetween(fromStart, toExclusive);

        return DashboardSummaryResponse.builder()
                .from(f)
                .to(t)
                .appointments(appointmentFactRepository.countAppointmentsBetween(f, t))
                .completedAppointments(appointmentFactRepository.countByStatusBetween(f, t, STATUS_COMPLETED))
                .cancelledAppointments(appointmentFactRepository.countByStatusBetween(f, t, STATUS_CANCELLED))
                .invoicedRevenue(invoiced)
                .collectedRevenue(collected)
                .refundedRevenue(refunded)
                .outstandingRevenue(invoiced.subtract(collected))
                .activePatients(appointmentFactRepository.countDistinctPatientsBetween(f, t))
                .doctors(appointmentFactRepository.countDistinctDoctorsBetween(f, t))
                .build();
    }

    @Transactional(readOnly = true)
    public AppointmentsResponse getAppointments(LocalDate from, LocalDate to) {
        LocalDate f = resolveFrom(from, to);
        LocalDate t = resolveTo(to);

        List<AppointmentsResponse.StatusCount> statusBreakdown =
                appointmentFactRepository.countByStatusGroupedBetween(f, t).stream()
                        .map(row -> AppointmentsResponse.StatusCount.builder()
                                .status(row[0] == null ? "UNKNOWN" : String.valueOf(row[0]))
                                .count(((Number) row[1]).longValue())
                                .build())
                        .toList();

        List<AppointmentsResponse.DailyCount> dailyTrend =
                appointmentFactRepository.dailyCountBetween(f, t).stream()
                        .map(row -> AppointmentsResponse.DailyCount.builder()
                                .date(toLocalDate(row[0]))
                                .count(((Number) row[1]).longValue())
                                .build())
                        .toList();

        return AppointmentsResponse.builder()
                .from(f)
                .to(t)
                .totalAppointments(appointmentFactRepository.countAppointmentsBetween(f, t))
                .completedAppointments(appointmentFactRepository.countByStatusBetween(f, t, STATUS_COMPLETED))
                .cancelledAppointments(appointmentFactRepository.countByStatusBetween(f, t, STATUS_CANCELLED))
                .statusBreakdown(statusBreakdown)
                .dailyTrend(dailyTrend)
                .build();
    }

    @Transactional(readOnly = true)
    public RevenueResponse getRevenue(LocalDate from, LocalDate to) {
        LocalDate f = resolveFrom(from, to);
        LocalDate t = resolveTo(to);
        Instant fromStart = startOfDay(f);
        Instant toExclusive = startOfNextDay(t);

        BigDecimal invoiced = invoiceFactRepository.sumInvoicedBetween(fromStart, toExclusive);
        BigDecimal collected = paymentFactRepository.sumCollectedBetween(fromStart, toExclusive);
        BigDecimal refunded = paymentFactRepository.sumRefundedBetween(fromStart, toExclusive);

        TreeMap<LocalDate, RevenueResponse.MonthlyRevenue> byMonth = new TreeMap<>();
        invoiceFactRepository.monthlyInvoicedBetween(fromStart, toExclusive).forEach(row -> {
            LocalDate month = toLocalDate(row[0]);
            BigDecimal amount = toBigDecimal(row[1]);
            RevenueResponse.MonthlyRevenue existing = byMonth.get(month);
            byMonth.put(month, RevenueResponse.MonthlyRevenue.builder()
                    .month(formatMonth(month))
                    .invoiced(amount)
                    .collected(existing == null ? BigDecimal.ZERO : existing.getCollected())
                    .refunded(existing == null ? BigDecimal.ZERO : existing.getRefunded())
                    .build());
        });
        paymentFactRepository.monthlyPaymentsBetween(fromStart, toExclusive).forEach(row -> {
            LocalDate month = toLocalDate(row[0]);
            String status = String.valueOf(row[1]);
            BigDecimal amount = toBigDecimal(row[2]);
            RevenueResponse.MonthlyRevenue existing = byMonth.computeIfAbsent(month,
                    key -> RevenueResponse.MonthlyRevenue.builder()
                            .month(formatMonth(key))
                            .invoiced(BigDecimal.ZERO)
                            .collected(BigDecimal.ZERO)
                            .refunded(BigDecimal.ZERO)
                            .build());
            if (STATUS_COMPLETED.equals(status)) {
                byMonth.put(month, RevenueResponse.MonthlyRevenue.builder()
                        .month(existing.getMonth())
                        .invoiced(existing.getInvoiced())
                        .collected(existing.getCollected().add(amount))
                        .refunded(existing.getRefunded())
                        .build());
            } else if (STATUS_REFUNDED.equals(status)) {
                byMonth.put(month, RevenueResponse.MonthlyRevenue.builder()
                        .month(existing.getMonth())
                        .invoiced(existing.getInvoiced())
                        .collected(existing.getCollected())
                        .refunded(existing.getRefunded().add(amount))
                        .build());
            }
        });

        return RevenueResponse.builder()
                .from(f)
                .to(t)
                .invoiced(invoiced)
                .collected(collected)
                .refunded(refunded)
                .outstanding(invoiced.subtract(collected))
                .monthlyTrend(new ArrayList<>(byMonth.values()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<DoctorStatResponse> getDoctorStats(LocalDate from, LocalDate to) {
        LocalDate f = resolveFrom(from, to);
        LocalDate t = resolveTo(to);

        return appointmentFactRepository.doctorRankingBetween(f, t).stream()
                .map(row -> DoctorStatResponse.builder()
                        .doctorId((UUID) row[0])
                        .appointments(((Number) row[1]).longValue())
                        .completed(((Number) row[2]).longValue())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientsResponse getPatients(LocalDate from, LocalDate to) {
        LocalDate f = resolveFrom(from, to);
        LocalDate t = resolveTo(to);

        List<PatientsResponse.PatientActivityResponse> mostActive =
                appointmentFactRepository.mostActivePatientsBetween(f, t, MOST_ACTIVE_LIMIT).stream()
                        .map(row -> PatientsResponse.PatientActivityResponse.builder()
                                .patientId((UUID) row[0])
                                .appointments(((Number) row[1]).longValue())
                                .firstAppointment(row[2] == null ? null : toLocalDate(row[2]))
                                .build())
                        .toList();

        return PatientsResponse.builder()
                .from(f)
                .to(t)
                .totalPatients(appointmentFactRepository.countDistinctPatientsBetween(f, t))
                .newPatients(medicalRecordFactRepository.countNewPatientsBetween(f, t))
                .mostActive(mostActive)
                .build();
    }

    // ---------- helpers ----------

    private boolean isAlreadyProcessed(UUID eventId) {
        return processedEventRepository.existsById(eventId);
    }

    private void markProcessed(UUID eventId, String eventType) {
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
    }

    private void upsertAppointmentFact(AppointmentFact fact) {
        appointmentFactRepository.findByAppointmentId(fact.getAppointmentId()).ifPresentOrElse(existing -> {
            existing.setSourceEventId(fact.getSourceEventId());
            existing.setPatientId(fact.getPatientId());
            existing.setDoctorId(fact.getDoctorId());
            existing.setAppointmentDate(fact.getAppointmentDate());
            existing.setStartTime(fact.getStartTime());
            existing.setStatus(fact.getStatus());
            existing.setAppointmentType(fact.getAppointmentType());
            existing.setVersion(fact.getVersion());
            appointmentFactRepository.save(existing);
        }, () -> appointmentFactRepository.save(fact));
    }

    private void upsertInvoiceFact(InvoiceFact fact) {
        invoiceFactRepository.findByInvoiceId(fact.getInvoiceId()).ifPresentOrElse(existing -> {
            existing.setSourceEventId(fact.getSourceEventId());
            existing.setPatientId(fact.getPatientId());
            existing.setAppointmentId(fact.getAppointmentId());
            existing.setInvoiceNumber(fact.getInvoiceNumber());
            existing.setStatus(fact.getStatus());
            existing.setCurrency(fact.getCurrency());
            existing.setSubtotal(fact.getSubtotal());
            existing.setTaxAmount(fact.getTaxAmount());
            existing.setDiscount(fact.getDiscount());
            existing.setTotalAmount(fact.getTotalAmount());
            existing.setPaidAmount(fact.getPaidAmount());
            existing.setInvoiceDate(fact.getInvoiceDate());
            existing.setVersion(fact.getVersion());
            invoiceFactRepository.save(existing);
        }, () -> invoiceFactRepository.save(fact));
    }

    private LocalDate resolveTo(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }

    private LocalDate resolveFrom(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = resolveTo(to);
        LocalDate effectiveFrom = from != null ? from : effectiveTo.withDayOfMonth(1);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("'from' date cannot be after 'to' date");
        }
        return effectiveFrom;
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private String formatMonth(LocalDate month) {
        return MONTH_FORMAT.format(month);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        throw new IllegalArgumentException("Cannot convert " + value + " to LocalDate");
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}