package com.portfolio.tracker.assetprice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Projection Spring Data pour les requêtes natives de dernier prix. */
public interface LatestPriceProjection {
    String getSymbol();
    BigDecimal getPrice();
    String getCurrency();
    LocalDateTime getLastUpdated();
}