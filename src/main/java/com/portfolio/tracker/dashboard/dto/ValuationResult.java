package com.portfolio.tracker.dashboard.dto;

import com.portfolio.tracker.shared.MoneyConstants;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Résultat brut d'une valorisation, en EUR.
 * Objet interne : il est ensuite projeté vers les DTOs d'API
 * avec conversion vers la devise d'affichage.
 */
@Getter
@Builder
public class ValuationResult {

    private BigDecimal totalValueEur;
    private BigDecimal totalInvestedEur;
    private BigDecimal totalUnrealizedGainEur;
    private BigDecimal totalUnrealizedGainPercentage;
    private BigDecimal totalRealizedGainEur;
    private BigDecimal totalDividendsEur;
    private BigDecimal totalFeesEur;
    private BigDecimal totalInterestEur;
    /** Liquidités des portefeuilles suivis (livrets compris). */
    private BigDecimal totalCashEur;
    private BigDecimal totalNetDepositsEur;

    private List<PortfolioValuation> portfolios;
    private boolean hasIncompletePrices;

    public static ValuationResult empty() {
        return ValuationResult.builder()
                .totalValueEur(BigDecimal.ZERO)
                .totalInvestedEur(BigDecimal.ZERO)
                .totalUnrealizedGainEur(BigDecimal.ZERO)
                .totalUnrealizedGainPercentage(BigDecimal.ZERO)
                .totalRealizedGainEur(BigDecimal.ZERO)
                .totalDividendsEur(BigDecimal.ZERO)
                .totalFeesEur(BigDecimal.ZERO)
                .totalInterestEur(BigDecimal.ZERO)
                .totalCashEur(BigDecimal.ZERO)
                .totalNetDepositsEur(BigDecimal.ZERO)
                .portfolios(List.of())
                .hasIncompletePrices(false)
                .build();
    }

    /** Agrège une liste de portefeuilles valorisés en un total global. */
    public static ValuationResult aggregate(List<PortfolioValuation> portfolios) {
        if (portfolios == null || portfolios.isEmpty()) {
            return empty();
        }

        BigDecimal value = sum(portfolios, PortfolioValuation::getCurrentValueEur);
        BigDecimal invested = sum(portfolios, PortfolioValuation::getInvestedEur);
        BigDecimal unrealized = sum(portfolios, PortfolioValuation::getUnrealizedGainEur);
        BigDecimal cash = sum(portfolios, PortfolioValuation::getCashEur);

        return ValuationResult.builder()
                .totalValueEur(value)
                .totalInvestedEur(invested)
                .totalUnrealizedGainEur(unrealized)
                // % latent sur le prix de revient des positions : les liquidités ne le diluent pas
                .totalUnrealizedGainPercentage(percentage(unrealized, invested.subtract(cash)))
                .totalRealizedGainEur(sum(portfolios, PortfolioValuation::getRealizedGainEur))
                .totalDividendsEur(sum(portfolios, PortfolioValuation::getDividendsEur))
                .totalFeesEur(sum(portfolios, PortfolioValuation::getTotalFeesEur))
                .totalInterestEur(sum(portfolios, PortfolioValuation::getInterestEur))
                .totalCashEur(cash)
                .totalNetDepositsEur(sum(portfolios, PortfolioValuation::getNetDepositsEur))
                .portfolios(portfolios.stream()
                        .sorted(Comparator.comparing(PortfolioValuation::getName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                        .toList())
                .hasIncompletePrices(portfolios.stream().anyMatch(PortfolioValuation::isHasIncompletePrices))
                .build();
    }

    private static BigDecimal sum(List<PortfolioValuation> list,
                                  java.util.function.Function<PortfolioValuation, BigDecimal> getter) {
        return list.stream()
                .map(getter)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
    }

    private static BigDecimal percentage(BigDecimal gain, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return gain.multiply(BigDecimal.valueOf(100))
                .divide(base, MoneyConstants.PERCENT_SCALE, MoneyConstants.ROUNDING);
    }
}