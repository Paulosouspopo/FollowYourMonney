package com.portfolio.tracker.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurvePointDTO {
    private LocalDate date;
    private BigDecimal totalValueEur;
    private BigDecimal totalInvestedEur;
    private BigDecimal gainLossEur;
    private BigDecimal gainLossPercentage;
}