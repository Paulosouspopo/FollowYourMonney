package com.portfolio.tracker.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Prix d'un actif à un instant, dans sa devise de cotation.
 * {@code missing = true} signale qu'aucun prix n'est disponible.
 */
public record PriceSnapshot(
        String symbol,
        BigDecimal price,
        String currency,
        LocalDateTime asOf,
        boolean missing
) {
    public static PriceSnapshot missing(String symbol) {
        return new PriceSnapshot(symbol, BigDecimal.ZERO, null, null, true);
    }
}