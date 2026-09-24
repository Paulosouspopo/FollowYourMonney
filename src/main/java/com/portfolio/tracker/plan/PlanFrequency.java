package com.portfolio.tracker.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Périodicité d'un plan. La n-ième échéance est calculée depuis la date de
 * début (et non de proche en proche) : un plan mensuel démarré un 31 tombe le
 * dernier jour des mois courts sans « glisser » ensuite au 28.
 */
public enum PlanFrequency {
    DAILY("365.25"),
    WEEKLY("52"),
    BIWEEKLY("26"),
    MONTHLY("12"),
    QUARTERLY("4"),
    YEARLY("1");

    private final BigDecimal perYear;

    PlanFrequency(String perYear) {
        this.perYear = new BigDecimal(perYear);
    }

    /** Date de l'échéance numéro {@code n} (0 = date de début). */
    public LocalDate occurrence(LocalDate start, int n) {
        return switch (this) {
            case DAILY -> start.plusDays(n);
            case WEEKLY -> start.plusWeeks(n);
            case BIWEEKLY -> start.plusWeeks(2L * n);
            case MONTHLY -> start.plusMonths(n);
            case QUARTERLY -> start.plusMonths(3L * n);
            case YEARLY -> start.plusYears(n);
        };
    }

    /** Équivalent mensuel d'un montant par échéance (budget affiché). */
    public BigDecimal monthly(BigDecimal amount) {
        return amount.multiply(perYear).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
}
