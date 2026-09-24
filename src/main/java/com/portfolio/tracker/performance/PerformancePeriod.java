package com.portfolio.tracker.performance;

import com.portfolio.tracker.shared.exception.BadRequestException;

import java.time.LocalDate;

/** Période d'analyse : ?period=1m|3m|ytd|1y|3y|5y|all. */
public enum PerformancePeriod {
    ONE_MONTH("1m"), THREE_MONTHS("3m"), YEAR_TO_DATE("ytd"), ONE_YEAR("1y"), THREE_YEARS("3y"),
    FIVE_YEARS("5y"), ALL("all");

    private final String code;

    PerformancePeriod(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Premier jour de la période ; null = depuis le début (premier snapshot). */
    public LocalDate start(LocalDate today) {
        return switch (this) {
            case ONE_MONTH -> today.minusMonths(1).plusDays(1);
            case THREE_MONTHS -> today.minusMonths(3).plusDays(1);
            case YEAR_TO_DATE -> today.withDayOfYear(1);
            case ONE_YEAR -> today.minusYears(1).plusDays(1);
            case THREE_YEARS -> today.minusYears(3).plusDays(1);
            case FIVE_YEARS -> today.minusYears(5).plusDays(1);
            case ALL -> null;
        };
    }

    public static PerformancePeriod fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return ONE_YEAR;
        }
        for (PerformancePeriod p : values()) {
            if (p.code.equalsIgnoreCase(raw.trim())) {
                return p;
            }
        }
        throw new BadRequestException("Période invalide : '" + raw + "'. Valeurs acceptées : 1m, 3m, ytd, 1y, 3y, 5y, all");
    }
}
