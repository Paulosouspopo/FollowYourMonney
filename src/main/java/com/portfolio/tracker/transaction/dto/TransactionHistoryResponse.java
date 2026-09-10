package com.portfolio.tracker.transaction.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TransactionHistoryResponse(
        UUID assetId,
        String symbol,
        String assetName,
        String baseCurrency,

        BigDecimal totalQuantityHeld,
        BigDecimal averageCostPerUnit,
        BigDecimal totalInvested,
        BigDecimal totalFees,
        BigDecimal totalDividendsReceived,
        BigDecimal realizedGainLoss,

        List<TransactionResponse> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
