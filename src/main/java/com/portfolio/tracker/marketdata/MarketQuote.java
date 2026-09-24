package com.portfolio.tracker.marketdata;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Résultat neutre (indépendant du provider) d'une cotation.
 */
public record MarketQuote(
                String symbol,
                BigDecimal price,
                String currency,
                LocalDateTime asOf,
                String longName,
                String exchangeName,
                String instrumentType // "EQUITY", "ETF", "CRYPTOCURRENCY"... (meta.instrumentType)
) {
}