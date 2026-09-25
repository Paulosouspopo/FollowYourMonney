package com.portfolio.tracker.income;

import com.portfolio.tracker.marketdata.DividendEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Projection des dividendes d'une ligne à partir des 12 derniers mois
 * (sans réseau, testée à part). Hypothèse simple et explicable : l'année qui
 * vient ressemble à l'année écoulée, aux mêmes dates.
 */
public final class DividendProjection {

    private DividendProjection() {
    }

    /** Dividendes détachés sur les 365 derniers jours. */
    public static List<DividendEvent> lastYear(List<DividendEvent> events, LocalDate today) {
        LocalDate from = today.minusDays(365);
        return events.stream().filter(e -> e.exDate().isAfter(from) && !e.exDate().isAfter(today)).toList();
    }

    /** Montant annuel par action (somme des 12 derniers mois). */
    public static BigDecimal perShare(List<DividendEvent> lastYear) {
        return lastYear.stream().map(DividendEvent::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Prochains versements estimés : chaque dividende de l'an dernier, un an
     * plus tard, s'il tombe dans les 12 mois à venir (aujourd'hui compris).
     */
    public static List<DividendEvent> nextYear(List<DividendEvent> lastYear, LocalDate today) {
        return lastYear.stream()
                .map(e -> new DividendEvent(e.symbol(), e.exDate().plusYears(1), e.amount(), e.currency()))
                .filter(e -> !e.exDate().isBefore(today) && !e.exDate().isAfter(today.plusDays(365)))
                .toList();
    }
}
