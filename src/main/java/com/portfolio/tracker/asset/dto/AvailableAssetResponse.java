package com.portfolio.tracker.asset.dto;

import com.portfolio.tracker.asset.AssetType;

import lombok.Builder;

@Builder
public record AvailableAssetResponse(
        String symbol,
        String name,
        AssetType type,
        String currency
) {}