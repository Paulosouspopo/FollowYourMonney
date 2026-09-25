package com.portfolio.tracker.watchlist.dto;

import com.portfolio.tracker.asset.AssetType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ligne de la liste « Marchés ». Chiffres lus en base (cotation horaire),
 * sans appel réseau.
 *
 * @param price          dernier cours, devise de cotation
 * @param dayChangePct   variation vs clôture précédente (null si inconnue)
 * @param sparkline      clôtures des 30 derniers jours (devise de cotation)
 * @param ownedQuantity  quantité détenue, tous portefeuilles (0 = non détenu)
 * @param alertCount     alertes actives sur ce symbole
 */
public record WatchlistItemResponse(
        UUID id,
        String symbol,
        String name,
        AssetType assetType,
        BigDecimal price,
        String currency,
        LocalDate priceDate,
        BigDecimal dayChangePct,
        List<BigDecimal> sparkline,
        BigDecimal ownedQuantity,
        int alertCount
) {
}
