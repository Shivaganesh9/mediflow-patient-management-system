package com.mediflow.analytics.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class AppointmentsResponse {
    LocalDate from;
    LocalDate to;
    long totalAppointments;
    long completedAppointments;
    long cancelledAppointments;
    List<StatusCount> statusBreakdown;
    List<DailyCount> dailyTrend;

    @Value
    @Builder
    public static class StatusCount {
        String status;
        long count;
    }

    @Value
    @Builder
    public static class DailyCount {
        LocalDate date;
        long count;
    }
}