package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetPerformanceDTO {

    private UUID assetId;
    private String symbol;
    private String name;
    private String assetType;               // ACTION, ETF, CRYPTO, etc.
    
    private BigDecimal currentPrice;        // Prix actuel
    private BigDecimal quantity;            // Quantité détenue
    private BigDecimal currentValue;        // quantity * currentPrice
    
    private BigDecimal averageCostPerUnit;  // Prix moyen d'achat
    private BigDecimal investedAmount;      // Montant total investi
    
    private BigDecimal gainLoss;            // Non-réalisé : (currentValue - investedAmount)
    private BigDecimal gainLossPercentage;
    
    private String currency;
}