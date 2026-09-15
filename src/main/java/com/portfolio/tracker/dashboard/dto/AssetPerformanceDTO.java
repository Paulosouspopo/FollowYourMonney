package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

import com.portfolio.tracker.asset.AssetType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetPerformanceDTO {
    private UUID assetId;
    private String symbol;
    private String name;
    private AssetType assetType;
    private BigDecimal currentPrice;
    private BigDecimal quantity;
    private BigDecimal currentValue;
    private BigDecimal averageCostPerUnit;
    private BigDecimal investedAmount;
    private BigDecimal gainLoss; // latent
    private BigDecimal gainLossPercentage;
    private BigDecimal realizedGainLoss; // réalisé (ventes + dividendes)
    private boolean priceAvailable;
    private String currency;
}