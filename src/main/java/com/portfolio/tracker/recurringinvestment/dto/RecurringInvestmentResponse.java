package com.portfolio.tracker.recurringinvestment.dto;

import com.portfolio.tracker.recurringinvestment.Frequency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecurringInvestmentResponse(
        UUID id,
        UUID assetId,
        BigDecimal amount,
        Frequency frequency,
        LocalDateTime startDate,
        LocalDateTime nextExecution,
        LocalDateTime endDate,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}