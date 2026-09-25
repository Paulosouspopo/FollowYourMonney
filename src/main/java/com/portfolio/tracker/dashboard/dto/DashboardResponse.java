package com.portfolio.tracker.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
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
    /** Montants de la courbe dans {@link #curveCurrency} (taux historique de chaque jour). */
    private List<CurvePointDTO> curve;
    private String curveCurrency;

    /** Vue globale : tendance sur 30 jours de chaque portefeuille (tuiles). */
    private List<PortfolioTrend> trends;
}