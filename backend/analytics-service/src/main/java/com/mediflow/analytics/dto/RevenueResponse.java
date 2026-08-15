package com.mediflow.analytics.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class RevenueResponse {
    LocalDate from;
    LocalDate to;
    BigDecimal invoiced;
    BigDecimal collected;
    BigDecimal refunded;
    BigDecimal outstanding;
    List<MonthlyRevenue> monthlyTrend;

    @Value
    @Builder
    public static class MonthlyRevenue {
        String month;
        BigDecimal invoiced;
        BigDecimal collected;
        BigDecimal refunded;
    }
}