package com.portfolio.tracker.cash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * Intérêts d'une année sur un solde rémunéré, à partir de ses mouvements.
 * Calcul pur (testé), sans accès aux données.
 *
 * <ul>
 * <li>Livret réglementé : règle des quinzaines. Un dépôt rapporte à partir du
 * 1er ou du 16 qui suit ; un retrait cesse de rapporter dès le début de sa
 * quinzaine. Chaque quinzaine rapporte taux / 24.</li>
 * <li>Fonds euros : prorata journalier (taux / nombre de jours de l'année).</li>
 * </ul>
 * Estimation : le taux réel du fonds euros n'est connu qu'en fin d'année, et
 * certains livrets ont des règles propres (plafonds, taux bonifiés).
 */
public final class InterestEstimator {

    public enum Method { QUINZAINE, DAILY }

    /** Variation du solde rémunéré à une date (positive = entrée d'argent). */
    public record Flow(LocalDate date, BigDecimal amount) {
    }

    private InterestEstimator() {
    }

    /**
     * Intérêts de {@code year} jusqu'à {@code until} inclus (31/12 pour une
     * année écoulée), au taux annuel {@code ratePercent}.
     */
    public static BigDecimal estimate(List<Flow> flows, BigDecimal ratePercent, int year, LocalDate until,
            Method method) {
        if (ratePercent == null || ratePercent.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        LocalDate end = until.isAfter(LocalDate.of(year, 12, 31)) ? LocalDate.of(year, 12, 31) : until;
        if (end.getYear() < year) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal rate = ratePercent.movePointLeft(2);
        BigDecimal total = method == Method.QUINZAINE
                ? quinzaines(flows, rate, year, end)
                : daily(flows, rate, year, end);
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal quinzaines(List<Flow> flows, BigDecimal rate, int year, LocalDate end) {
        BigDecimal perQuinzaine = rate.divide(BigDecimal.valueOf(24), 12, RoundingMode.HALF_UP);
        BigDecimal total = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            for (int half = 0; half < 2; half++) {
                LocalDate start = LocalDate.of(year, month, half == 0 ? 1 : 16);
                if (start.isAfter(end)) {
                    return total;
                }
                LocalDate last = half == 0 ? LocalDate.of(year, month, 15) : start.withDayOfMonth(start.lengthOfMonth());
                BigDecimal base = BigDecimal.ZERO;
                for (Flow f : flows) {
                    boolean deposit = f.amount().signum() > 0;
                    // Dépôt : compte s'il est antérieur au début de la quinzaine ; retrait : dès sa quinzaine
                    if (deposit ? f.date().isBefore(start) : !f.date().isAfter(last)) {
                        base = base.add(f.amount());
                    }
                }
                if (base.signum() > 0) {
                    total = total.add(base.multiply(perQuinzaine));
                }
            }
        }
        return total;
    }

    private static BigDecimal daily(List<Flow> flows, BigDecimal rate, int year, LocalDate end) {
        BigDecimal perDay = rate.divide(BigDecimal.valueOf(Year.of(year).length()), 12, RoundingMode.HALF_UP);
        List<Flow> sorted = flows.stream().sorted(java.util.Comparator.comparing(Flow::date)).toList();
        BigDecimal balance = BigDecimal.ZERO;
        int next = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate day = LocalDate.of(year, 1, 1); !day.isAfter(end); day = day.plusDays(1)) {
            while (next < sorted.size() && !sorted.get(next).date().isAfter(day)) {
                balance = balance.add(sorted.get(next++).amount());
            }
            if (balance.signum() > 0) {
                total = total.add(balance.multiply(perDay));
            }
        }
        return total;
    }
}
