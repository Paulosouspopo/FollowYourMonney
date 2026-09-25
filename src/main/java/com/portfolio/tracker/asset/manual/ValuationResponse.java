package com.portfolio.tracker.asset.manual;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ValuationResponse(LocalDate date, BigDecimal price, String currency) {}
