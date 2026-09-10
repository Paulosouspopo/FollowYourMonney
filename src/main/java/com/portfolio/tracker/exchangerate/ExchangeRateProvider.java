package com.portfolio.tracker.exchangerate;

import java.math.BigDecimal;
import java.util.Optional;

public interface ExchangeRateProvider {
    Optional<BigDecimal> getRate(String fromCurrency, String toCurrency);
}