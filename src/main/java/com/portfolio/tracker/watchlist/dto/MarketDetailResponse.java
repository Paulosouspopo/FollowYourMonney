package com.portfolio.tracker.watchlist.dto;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.notification.dto.AlertRuleResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fiche d'un actif (détenu ou non).
 *
 * @param priceEur    cours converti en EUR
 * @param low52w      plus bas des clôtures sur 1 an (devise de cotation)
 * @param watchlistId null si l'actif n'est pas suivi
 */
public record MarketDetailResponse(
        String symbol,
        String name,
        String exchange,
        AssetType assetType,
        BigDecimal price,
        String currency,
        BigDecimal priceEur,
        LocalDateTime asOf,
        LocalDate marketDate,
        BigDecimal dayChangePct,
        BigDecimal low52w,
        BigDecimal high52w,
        UUID watchlistId,
        List<Holding> holdings,
        List<AlertRuleResponse> alerts
) {
    /** Une ligne détenue (un portefeuille). */
    public record Holding(UUID portfolioId, String portfolioName, BigDecimal quantity, BigDecimal valueEur,
                          BigDecimal investedEur, BigDecimal gainEur, BigDecimal gainPct) {
    }
}
