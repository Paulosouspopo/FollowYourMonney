package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSnapshotDTO {

    private UUID id;
    private String name;
    private String type;                        // PortfolioType (PEA, CTO, CRYPTO, etc.)
    
    private BigDecimal currentValue;            // Valeur actuelle
    private BigDecimal investedAmount;          // Montant investi
    private BigDecimal gainLoss;                // Gain/perte
    private BigDecimal gainLossPercentage;
    
    private int assetCount;                     // Nombre d'actifs
    private List<AssetPerformanceDTO> assets;   // Détail des actifs du portefeuille
}