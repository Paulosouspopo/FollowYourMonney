package com.portfolio.tracker.marketdata;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketPricePoint(
        String symbol,
        BigDecimal price,
        String currency,
        LocalDateTime asOf) {
}