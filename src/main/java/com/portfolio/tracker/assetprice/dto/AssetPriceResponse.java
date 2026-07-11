package com.portfolio.tracker.assetprice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AssetPriceResponse(
        UUID id,
        String symbol,
        BigDecimal price,
        String currency,
        LocalDateTime lastUpdated,
        String source
) {}