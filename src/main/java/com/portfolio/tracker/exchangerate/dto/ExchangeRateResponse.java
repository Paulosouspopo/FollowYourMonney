package com.portfolio.tracker.exchangerate.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExchangeRateResponse(
        UUID id,
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        LocalDateTime lastUpdated,
        String source
) {}