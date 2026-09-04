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

    private BigDecimal totalValue;              // Valeur totale actuelle de tous les portefeuilles
    private BigDecimal totalInvested;           // Total investi (somme des achats nets)
    private BigDecimal totalGainLoss;           // Gain/perte réalisés + non-réalisés
    private BigDecimal gainLossPercentage;      // En pourcentage
    
    private List<PortfolioSnapshotDTO> portfolios;  // Vue par portefeuille
    private List<AllocationSliceDTO> allocation;    // Allocation globale (par type d'actif)
    private List<RecentTransactionDTO> recentTransactions; // 10 dernières transactions
    private List<EvolutionPointDTO> evolutionCurve; // Points pour graphique historique
}