package com.portfolio.tracker.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Résultat neutre (indépendant du provider) d'une cotation.
 *
 * @param asOf       moment où la cotation a été récupérée
 * @param marketDate jour de bourse auquel se rapporte le prix (fuseau de la
 *                   place). Un samedi, pour une action, c'est le vendredi.
 */
public record MarketQuote(
                String symbol,
                BigDecimal price,
                String currency,
                LocalDateTime asOf,
                LocalDate marketDate,
                String longName,
                String exchangeName,
                String instrumentType // "EQUITY", "ETF", "CRYPTOCURRENCY"... (meta.instrumentType)
) {
}
