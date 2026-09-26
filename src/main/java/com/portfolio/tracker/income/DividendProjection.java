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

    /**
     * Versements par an (12, 4, 2 ou 1), d'après l'écart médian entre deux
     * détachements sur deux ans. Compter les dividendes des 365 derniers jours
     * ne suffit pas : une date décalée de quelques jours fait passer un
     * trimestriel à 3 versements.
     */
    public static int paymentsPerYear(List<DividendEvent> events, LocalDate today) {
        List<LocalDate> dates = events.stream().map(DividendEvent::exDate)
                .filter(d -> d.isAfter(today.minusDays(730)) && !d.isAfter(today))
                .sorted().toList();
        if (dates.size() < 2) {
            return 1;
        }
        List<Long> gaps = new java.util.ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            gaps.add(java.time.temporal.ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
        }
        java.util.Collections.sort(gaps);
        long median = gaps.get(gaps.size() / 2);
        return median <= 45 ? 12 : median <= 135 ? 4 : median <= 270 ? 2 : 1;
    }

    /**
     * Le dernier cycle de versements : les {@link #paymentsPerYear} plus récents
     * détachés depuis 400 jours au plus (tolère un calendrier qui glisse).
     */
    public static List<DividendEvent> lastCycle(List<DividendEvent> events, LocalDate today) {
        int perYear = paymentsPerYear(events, today);
        List<DividendEvent> recent = events.stream()
                .filter(e -> e.exDate().isAfter(today.minusDays(400)) && !e.exDate().isAfter(today))
                .sorted(java.util.Comparator.comparing(DividendEvent::exDate))
                .toList();
        return recent.subList(Math.max(0, recent.size() - perYear), recent.size());
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
