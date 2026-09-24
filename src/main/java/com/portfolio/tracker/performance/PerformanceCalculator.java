package com.portfolio.tracker.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculs de performance, sans base ni réseau (testés à part).
 *
 * <ul>
 * <li><b>TWR</b> (rendement pondéré par le temps) : performance des placements
 * eux-mêmes, indépendante du moment et du montant des versements. Chaque jour
 * (Dietz) : r = (V(j) - V(j-1) - F(j)) / (V(j-1) + apport(j)). Un apport
 * arrive en début de journée (un achat mesure l'écart entre son prix et la
 * clôture), un retrait en fin de journée (une vente encaisse le mouvement du
 * jour). Comparable entre portefeuilles et avec un indice.</li>
 * <li><b>XIRR</b> (taux de rendement interne, pondéré par les montants) : le
 * rendement de TON argent, versements compris à leur date. Annualisé.</li>
 * </ul>
 */
public final class PerformanceCalculator {

    /** En deçà, un jour sans capital investi : pas de rendement mesurable (évite 0/0). */
    private static final double MIN_CAPITAL = 0.01;

    private PerformanceCalculator() {
    }

    /** Un jour : valeur de clôture (pour la performance) et flux externe du jour. */
    public record Day(LocalDate date, double value, double flow) {
    }

    /** @param twrCumulative TWR cumulé depuis le début de la période (0.05 = +5 %), jour par jour */
    public record Result(double startValue, double endValue, double netFlows, double gain,
                         double twr, Double xirr, double mwr, List<Double> twrCumulative) {
    }

    /**
     * @param baseDate  veille du début de la période
     * @param baseValue valeur à cette date (0 si le portefeuille n'existait pas)
     * @param days      jours de la période, triés, sans trou
     */
    public static Result compute(LocalDate baseDate, double baseValue, List<Day> days) {
        double index = 1;
        double previous = baseValue;
        double flows = 0;
        List<Double> cumulative = new ArrayList<>(days.size());
        List<double[]> cashFlows = new ArrayList<>(); // {années depuis baseDate, montant côté investisseur}
        if (baseValue > 0) {
            cashFlows.add(new double[] { 0, -baseValue });
        }
        for (Day d : days) {
            double capital = previous + Math.max(d.flow(), 0);
            if (capital > MIN_CAPITAL) {
                index *= 1 + (d.value() - previous - d.flow()) / capital;
            }
            cumulative.add(index - 1);
            flows += d.flow();
            if (d.flow() != 0) {
                // Apport en début de journée (= clôture de la veille), retrait en fin de journée
                LocalDate when = d.flow() > 0 ? d.date().minusDays(1) : d.date();
                cashFlows.add(new double[] { years(baseDate, when), -d.flow() });
            }
            previous = d.value();
        }
        double end = days.isEmpty() ? baseValue : days.get(days.size() - 1).value();
        LocalDate endDate = days.isEmpty() ? baseDate : days.get(days.size() - 1).date();
        double horizon = years(baseDate, endDate);
        cashFlows.add(new double[] { horizon, end });

        Double xirr = xirr(cashFlows);
        double mwr = xirr == null ? 0 : Math.pow(1 + xirr, horizon) - 1;
        return new Result(baseValue, end, flows, end - baseValue - flows, index - 1, xirr, mwr, cumulative);
    }

    /**
     * Taux annuel x tel que Σ montant / (1+x)^t = 0. Newton, puis dichotomie si
     * Newton ne converge pas. Null si aucun investissement ou pas de solution.
     */
    static Double xirr(List<double[]> flows) {
        boolean hasOut = flows.stream().anyMatch(f -> f[1] < 0);
        boolean hasIn = flows.stream().anyMatch(f -> f[1] > 0);
        if (!hasOut || !hasIn) {
            return null;
        }
        double x = 0.1;
        for (int i = 0; i < 100; i++) {
            double npv = npv(flows, x);
            double derivative = 0;
            for (double[] f : flows) {
                derivative -= f[0] * f[1] / Math.pow(1 + x, f[0] + 1);
            }
            if (derivative == 0 || Double.isNaN(derivative)) {
                break;
            }
            double nextX = x - npv / derivative;
            if (nextX <= -0.999999 || Double.isNaN(nextX) || Double.isInfinite(nextX)) {
                break;
            }
            if (Math.abs(nextX - x) < 1e-10) {
                return nextX;
            }
            x = nextX;
        }
        return bisection(flows);
    }

    private static Double bisection(List<double[]> flows) {
        double low = -0.9999;
        double high = 10;
        double fLow = npv(flows, low);
        double fHigh = npv(flows, high);
        if (Double.isNaN(fLow) || Double.isNaN(fHigh) || fLow * fHigh > 0) {
            return null;
        }
        for (int i = 0; i < 200; i++) {
            double mid = (low + high) / 2;
            double fMid = npv(flows, mid);
            if (Math.abs(fMid) < 1e-9 || high - low < 1e-12) {
                return mid;
            }
            if (fLow * fMid < 0) {
                high = mid;
            } else {
                low = mid;
                fLow = fMid;
            }
        }
        return (low + high) / 2;
    }

    private static double npv(List<double[]> flows, double rate) {
        double sum = 0;
        for (double[] f : flows) {
            sum += f[1] / Math.pow(1 + rate, f[0]);
        }
        return sum;
    }

    static double years(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) / 365.0;
    }
}
