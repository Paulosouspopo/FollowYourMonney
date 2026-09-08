package com.portfolio.tracker.asset.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.portfolio.tracker.asset.AssetType;

import lombok.Builder;

@Builder
public record AssetSummaryResponse(
        UUID id,
        String symbol,
        String name,
        AssetType assetType,
        String currency,
        BigDecimal currentPrice,      // dernier prix connu (via AssetPrice)
        BigDecimal quantityHeld,      // quantité totale détenue (calculée)
        BigDecimal currentValue       // quantityHeld * currentPrice
) {}
