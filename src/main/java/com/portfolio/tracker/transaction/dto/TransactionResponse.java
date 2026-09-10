package com.portfolio.tracker.transaction.dto;

import com.portfolio.tracker.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID assetId,
        UUID portfolioId,
        String symbol,
        String assetName,
        TransactionType type,
        BigDecimal quantity,
        BigDecimal pricePerUnit,
        BigDecimal fees,
        BigDecimal totalAmount,
        String currency,
        BigDecimal exchangeRate,
        String baseCurrency,
        BigDecimal totalAmountInBaseCurrency,
        LocalDateTime transactionDate,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

