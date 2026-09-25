package com.portfolio.tracker.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dividende versé par action (date de détachement), dans la devise de cotation.
 */
public record DividendEvent(String symbol, LocalDate exDate, BigDecimal amount, String currency) {
}
