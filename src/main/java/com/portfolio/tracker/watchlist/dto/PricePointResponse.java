package com.portfolio.tracker.watchlist.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricePointResponse(LocalDate date, BigDecimal close) {
}
