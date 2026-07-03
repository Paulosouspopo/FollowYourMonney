package com.portfolio.tracker.transaction.dto;

import com.portfolio.tracker.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID assetId,
        TransactionType type,
        BigDecimal quantity,
        BigDecimal pricePerUnit,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime transactionDate,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
