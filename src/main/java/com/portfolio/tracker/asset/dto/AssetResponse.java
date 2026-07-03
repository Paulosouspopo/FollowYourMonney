package com.portfolio.tracker.asset.dto;

import com.portfolio.tracker.asset.AssetType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String symbol,
        String name,
        AssetType assetType,
        String currency,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}