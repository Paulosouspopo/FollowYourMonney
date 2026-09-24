package com.portfolio.tracker.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {
    private BigDecimal totalValueEur;
    private BigDecimal totalInvestedEur;
    private BigDecimal unrealizedGainEur;
    private BigDecimal unrealizedGainPercentage;
    private BigDecimal realizedGainEur;
    private BigDecimal dividendsEur;
    private BigDecimal totalFeesEur;
    private BigDecimal interestEur;
    /** Liquidités suivies (livrets compris), incluses dans totalValueEur. */
    private BigDecimal cashEur;
    private BigDecimal netDepositsEur;
    private boolean hasIncompletePrices;

    private List<PortfolioValuation> portfolios;
    private List<AllocationSliceDTO> allocation;
    private List<CurvePointDTO> curve;
}