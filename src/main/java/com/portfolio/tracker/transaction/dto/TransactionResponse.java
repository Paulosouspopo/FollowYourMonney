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

        /** Taux figé vers EUR au moment de l'opération. */
        BigDecimal exchangeRateToEur,
        /** Montant en devise pivot, figé. */
        BigDecimal totalAmountEur,
        BigDecimal feesEur,

        LocalDateTime transactionDate,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}