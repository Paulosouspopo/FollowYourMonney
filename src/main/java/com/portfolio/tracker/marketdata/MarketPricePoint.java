package com.portfolio.tracker.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Clôture journalière d'un symbole.
 *
 * @param date jour de cotation, dans le fuseau de la place de marché
 * @param asOf horodatage brut renvoyé par le provider (heure locale serveur)
 */
public record MarketPricePoint(
        String symbol,
        BigDecimal price,
        String currency,
        LocalDate date,
        LocalDateTime asOf) {
}
