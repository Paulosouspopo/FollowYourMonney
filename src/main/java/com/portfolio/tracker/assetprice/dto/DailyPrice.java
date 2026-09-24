package com.portfolio.tracker.assetprice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Point d'une série journalière chargée en mémoire (valorisation historique). */
public record DailyPrice(LocalDate date, BigDecimal price, String currency) {
}
