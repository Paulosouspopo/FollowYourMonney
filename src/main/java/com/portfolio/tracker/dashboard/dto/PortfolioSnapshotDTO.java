package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.portfolio.tracker.portfolio.PortfolioType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSnapshotDTO {
    private UUID portfolioId;
    private String portfolioName;
    private PortfolioType portfolioType;
    private BigDecimal currentValue;
    private BigDecimal investedAmount;
    private BigDecimal gainLoss;
    private BigDecimal gainLossPercentage;
    private int assetCount;
    private List<AssetPerformanceDTO> assets;
    private String currency;
}