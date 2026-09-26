package com.portfolio.tracker.performance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Indicateurs de risque (calcul pur, testé) à partir de la performance TWR
 * cumulée jour par jour : les versements n'y créent ni hausse ni chute.
 *
 * <ul>
 * <li>Volatilité : écart-type des variations quotidiennes des jours ouvrés,
 * annualisé (× √252).</li>
 * <li>Pire baisse (max drawdown) : plus forte chute d'un plus haut à un plus
 * bas, avec ses dates.</li>
 * <li>Ratio de Sharpe : (rendement annualisé − taux sans risque) / volatilité.</li>
 * <li>Meilleur et pire jour.</li>
 * </ul>
 * Rien sous 20 jours ouvrés : trop peu de points pour être parlant.
 */
public final class RiskCalculator {

    /** Taux sans risque supposé (rémunération d'un placement sans risque en euros). */
    static final double RISK_FREE = 0.02;
    static final int MIN_DAYS = 20;

    private RiskCalculator() {
    }

    public record Risk(double volatilityPct, double maxDrawdownPct, LocalDate drawdownPeak, LocalDate drawdownTrough,
                       Double sharpe, double bestDayPct, LocalDate bestDay, double worstDayPct, LocalDate worstDay,
                       double positiveDaysPct) {
    }

    /**
     * @param dates      jours de la période
     * @param cumulative TWR cumulé depuis le début de la période (0,05 = +5 %), un par jour
     * @return null si trop peu de jours ouvrés
     */
    public static Risk compute(List<LocalDate> dates, List<Double> cumulative) {
        List<Double> returns = new ArrayList<>();
        List<LocalDate> returnDays = new ArrayList<>();
        double previous = 1;
        double peak = 1, maxDrawdown = 0;
        LocalDate peakDay = dates.isEmpty() ? null : dates.get(0), ddPeak = null, ddTrough = null;
        for (int i = 0; i < dates.size(); i++) {
            double index = 1 + cumulative.get(i);
            LocalDate d = dates.get(i);
            if (index > peak) {
                peak = index;
                peakDay = d;
            }
            double drawdown = peak > 0 ? index / peak - 1 : 0;
            if (drawdown < maxDrawdown) {
                maxDrawdown = drawdown;
                ddPeak = peakDay;
                ddTrough = d;
            }
            // Week-end : pas de cotation, variation nulle qui fausserait la volatilité
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                if (previous > 0) {
                    returns.add(index / previous - 1);
                    returnDays.add(d);
                }
                previous = index;
            }
        }
        if (returns.size() < MIN_DAYS) {
            return null;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream().mapToDouble(r -> (r - mean) * (r - mean)).sum() / (returns.size() - 1);
        double volatility = Math.sqrt(variance) * Math.sqrt(252);

        int best = 0, worst = 0, positive = 0;
        for (int i = 0; i < returns.size(); i++) {
            if (returns.get(i) > returns.get(best)) best = i;
            if (returns.get(i) < returns.get(worst)) worst = i;
            if (returns.get(i) > 0) positive++;
        }
        double total = 1 + cumulative.get(cumulative.size() - 1);
        double years = Math.max(dates.get(0).until(dates.get(dates.size() - 1)).toTotalMonths() / 12.0,
                returns.size() / 252.0);
        double annualized = years >= 1 ? Math.pow(total, 1 / years) - 1 : total - 1;
        Double sharpe = volatility > 0 && years >= 1 ? round2((annualized - RISK_FREE) / volatility) : null;

        return new Risk(pct(volatility), pct(maxDrawdown), ddPeak, ddTrough, sharpe,
                pct(returns.get(best)), returnDays.get(best), pct(returns.get(worst)), returnDays.get(worst),
                pct((double) positive / returns.size()));
    }

    private static double pct(double ratio) {
        return Math.round(ratio * 10000) / 100.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
