package com.portfolio.tracker.portfolio.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.portfolio.PortfolioType;

public record PortfolioDetailResponse(
        UUID id,
        String name,
        String description,
        PortfolioType type,
        UUID userId,
        List<AssetResponse> assets,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
