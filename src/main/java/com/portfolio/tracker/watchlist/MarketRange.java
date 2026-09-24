package com.portfolio.tracker.watchlist;

import com.portfolio.tracker.shared.exception.BadRequestException;

import java.time.LocalDate;

/** Plage d'un graphique de cours. */
public enum MarketRange {
    M1(30), M3(91), M6(182), Y1(365), Y5(1826);

    private final int days;

    MarketRange(int days) {
        this.days = days;
    }

    public LocalDate from(LocalDate today) {
        return today.minusDays(days);
    }

    public static MarketRange fromCode(String code) {
        return switch (code == null ? "1Y" : code.toUpperCase()) {
            case "1M" -> M1;
            case "3M" -> M3;
            case "6M" -> M6;
            case "1Y" -> Y1;
            case "5Y" -> Y5;
            default -> throw new BadRequestException("Période inconnue : " + code + " (1M, 3M, 6M, 1Y, 5Y)");
        };
    }
}
