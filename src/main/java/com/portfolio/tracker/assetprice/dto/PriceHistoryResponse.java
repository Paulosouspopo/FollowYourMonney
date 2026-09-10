package com.portfolio.tracker.assetprice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PriceHistoryResponse(
        String symbol,
        String currency,
        List<PricePoint> points
) {
    public record PricePoint(LocalDateTime date, BigDecimal price) {}
}
