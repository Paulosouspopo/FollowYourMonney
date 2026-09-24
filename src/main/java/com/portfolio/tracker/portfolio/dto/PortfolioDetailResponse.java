package com.portfolio.tracker.portfolio.dto;

import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.portfolio.PortfolioType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PortfolioDetailResponse(
        UUID id,
        String name,
        String description,
        PortfolioType type,
        boolean cashTracking,
        BigDecimal annualInterestRate,
        UUID userId,
        List<AssetResponse> assets,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
