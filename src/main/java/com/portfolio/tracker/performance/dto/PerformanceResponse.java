package com.portfolio.tracker.performance.dto;

import com.portfolio.tracker.portfolio.PortfolioType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Performance sur une période, en EUR. Pourcentages en « 12.34 » (= 12,34 %).
 *
 * @param twrPct           rendement des placements (TWR), cumulé sur la période
 * @param twrAnnualizedPct TWR annualisé, seulement si la période dépasse un an
 * @param mwrPct           rendement de l'argent investi (pondéré par les montants), sur la période
 * @param xirrPct          le même annualisé (XIRR), seulement si la période dépasse un an
 * @param gainEur          valeur finale - valeur initiale - apports nets
 * @param benchmark        indice de comparaison (null si non demandé ou introuvable)
 * @param series           un point par jour : valeur et TWR cumulé, indice rebasé à 0 %
 * @param portfolios       détail par portefeuille (vue globale uniquement), même période
 * @param risk             volatilité, pire baisse, Sharpe… (null sous 20 jours ouvrés)
 */
public record PerformanceResponse(
        String period,
        LocalDate from,
        LocalDate to,
        BigDecimal startValueEur,
        BigDecimal endValueEur,
        BigDecimal netFlowsEur,
        BigDecimal gainEur,
        BigDecimal twrPct,
        BigDecimal twrAnnualizedPct,
        BigDecimal mwrPct,
        BigDecimal xirrPct,
        Benchmark benchmark,
        List<Point> series,
        List<PortfolioPerformance> portfolios,
        com.portfolio.tracker.performance.RiskCalculator.Risk risk
) {
    public record Point(LocalDate date, BigDecimal valueEur, BigDecimal twrPct, BigDecimal benchmarkPct) {
    }

    /** @param returnPct variation de l'indice en EUR sur la même période */
    public record Benchmark(String symbol, String name, BigDecimal returnPct) {
    }

    public record PortfolioPerformance(UUID portfolioId, String name, PortfolioType type, BigDecimal valueEur,
                                       BigDecimal gainEur, BigDecimal twrPct, BigDecimal mwrPct, BigDecimal xirrPct) {
    }
}
