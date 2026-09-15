package com.portfolio.tracker.transaction.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Historique d'un actif. Tous les agrégats sont en EUR. */
public record TransactionHistoryResponse(
        UUID assetId,
        String symbol,
        String assetName,

        BigDecimal totalQuantityHeld,
        BigDecimal averageCostPerUnitEur,
        BigDecimal totalInvestedEur,
        BigDecimal totalFeesEur,
        BigDecimal totalDividendsReceivedEur,
        BigDecimal realizedGainLossEur,

        List<TransactionResponse> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}