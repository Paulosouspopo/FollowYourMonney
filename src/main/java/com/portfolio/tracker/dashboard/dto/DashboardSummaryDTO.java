package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryDTO {
    private BigDecimal totalValue;
    private BigDecimal totalInvested;
    private BigDecimal totalGainLoss;
    private BigDecimal gainLossPercentage;
    private String currency;
    private List<PortfolioSnapshotDTO> portfolios;
    private List<AllocationSliceDTO> allocation;
    private List<RecentTransactionDTO> recentTransactions;
    private List<EvolutionPointDTO> evolutionCurve;
}