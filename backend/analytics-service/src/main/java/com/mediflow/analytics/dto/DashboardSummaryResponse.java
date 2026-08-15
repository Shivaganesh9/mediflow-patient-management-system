package com.mediflow.analytics.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class DashboardSummaryResponse {
    LocalDate from;
    LocalDate to;
    long appointments;
    long completedAppointments;
    long cancelledAppointments;
    BigDecimal invoicedRevenue;
    BigDecimal collectedRevenue;
    BigDecimal refundedRevenue;
    BigDecimal outstandingRevenue;
    long activePatients;
    long doctors;
}