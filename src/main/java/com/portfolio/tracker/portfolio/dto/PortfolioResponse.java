package com.portfolio.tracker.portfolio.dto;

import com.portfolio.tracker.portfolio.PortfolioType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PortfolioResponse(
        UUID id,
        String name,
        String description,
        PortfolioType type,
        boolean cashTracking,
        BigDecimal annualInterestRate,
        UUID userId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
