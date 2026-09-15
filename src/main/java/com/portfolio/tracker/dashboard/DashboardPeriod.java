package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.shared.exception.BadRequestException;

import java.time.LocalDate;

/**
 * Période demandée pour la courbe d'évolution du dashboard.
 * Paramètre d'URL : ?period=7d|30d|90d|1y|all
 */
public enum DashboardPeriod {
    SEVEN_DAYS("7d", 7),
    THIRTY_DAYS("30d", 30),
    NINETY_DAYS("90d", 90),
    ONE_YEAR("1y", 365),
    ALL("all", null);

    private final String code;
    private final Integer days;

    DashboardPeriod(String code, Integer days) {
        this.code = code;
        this.days = days;
    }

    public static DashboardPeriod fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return THIRTY_DAYS;
        }
        for (DashboardPeriod p : values()) {
            if (p.code.equalsIgnoreCase(raw.trim())) {
                return p;
            }
        }
        throw new BadRequestException(
                "Période invalide : '" + raw + "'. Valeurs acceptées : 7d, 30d, 90d, 1y, all");
    }

    /** Date de début de la fenêtre, ou null si ALL (pas de borne inférieure). */
    public LocalDate startDate(LocalDate reference) {
        return (days == null) ? null : reference.minusDays(days);
    }

    public String code() {
        return code;
    }
}