package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.assetprice.PriceHistoryService;
import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.exchangerate.DisplayCurrency;
import com.portfolio.tracker.exchangerate.ExchangeRateService;
import com.portfolio.tracker.exchangerate.FxSymbols;
import com.portfolio.tracker.portfolio.PortfolioRules;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.dashboard.dto.*;
import com.portfolio.tracker.snapshot.PortfolioSnapshot;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import com.portfolio.tracker.shared.MoneyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final PortfolioValuationService valuationService;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PriceHistoryService priceHistoryService;
    private final ExchangeRateService exchangeRateService;

    /** Dashboard global : tous les portefeuilles de l'utilisateur. */
    public DashboardResponse getDashboard(UUID userId, String periodCode) {
        return getDashboard(userId, periodCode, null);
    }

    public DashboardResponse getDashboard(UUID userId, String periodCode, String currencyCode) {
        DashboardPeriod period = DashboardPeriod.fromCode(periodCode);
        String currency = DisplayCurrency.of(currencyCode);

        ValuationResult valuation = valuationService.valuate(userId, null, null);
        List<CurvePointDTO> curve = convertCurve(buildCurveForUser(userId, period), currency);

        DashboardResponse response = toResponse(valuation, curve, currency);
        response.setTrends(trends(userId));
        return response;
    }

    /** Jours de la tendance affichée sur les tuiles de portefeuille. */
    private static final int TREND_DAYS = 30;

    /**
     * Tendance de chaque portefeuille sur 30 jours, en une seule requête :
     * valeurs quotidiennes et variation de plus-value (neutre vis-à-vis des
     * versements, comme l'en-tête du dashboard).
     */
    private List<PortfolioTrend> trends(UUID userId) {
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByUserIdAndSnapshotDateGreaterThanEqual(
                userId, LocalDate.now().minusDays(TREND_DAYS));
        Map<UUID, List<PortfolioSnapshot>> byPortfolio = snapshots.stream()
                .collect(Collectors.groupingBy(s -> s.getPortfolio().getId(), java.util.LinkedHashMap::new,
                        Collectors.toList()));
        return byPortfolio.entrySet().stream().map(e -> {
            List<PortfolioSnapshot> days = e.getValue(); // déjà triés par date
            PortfolioSnapshot first = days.get(0);
            PortfolioSnapshot last = days.get(days.size() - 1);
            BigDecimal change = last.getGainLoss().subtract(first.getGainLoss());
            BigDecimal pct = first.getTotalValue().signum() > 0
                    ? change.multiply(BigDecimal.valueOf(100))
                            .divide(first.getTotalValue(), MoneyConstants.PERCENT_SCALE, MoneyConstants.ROUNDING)
                    : null;
            return new PortfolioTrend(e.getKey(), days.stream().map(PortfolioSnapshot::getTotalValue).toList(),
                    change, pct);
        }).toList();
    }

    /**
     * Détail d'un portefeuille précis. Réutilise valuationService.valuate()
     * en filtrant sur portfolioId — quasi gratuit car même moteur de calcul.
     */
    public DashboardResponse getPortfolioDashboard(UUID userId, UUID portfolioId, String periodCode) {
        return getPortfolioDashboard(userId, portfolioId, periodCode, null);
    }

    public DashboardResponse getPortfolioDashboard(UUID userId, UUID portfolioId, String periodCode,
            String currencyCode) {
        DashboardPeriod period = DashboardPeriod.fromCode(periodCode);
        String currency = DisplayCurrency.of(currencyCode);

        // Lève ResourceNotFoundException si le portefeuille n'appartient pas à
        // l'utilisateur ; un portefeuille vide renvoie une valorisation neutre.
        ValuationResult valuation = valuationService.valuate(userId, portfolioId, null);

        List<CurvePointDTO> curve = convertCurve(buildCurveForPortfolio(portfolioId, period), currency);

        return toResponse(valuation, curve, currency);
    }

    /**
     * Courbe dans une autre devise : chaque jour au taux EUR→devise de ce
     * jour-là (série de la paire, comme pour les cours) ; aujourd'hui, taux
     * courant. Les pourcentages ne changent pas.
     */
    private List<CurvePointDTO> convertCurve(List<CurvePointDTO> curve, String currency) {
        if (curve.isEmpty() || MoneyConstants.BASE_CURRENCY.equals(currency)) {
            return curve;
        }
        LocalDate today = LocalDate.now();
        String pair = FxSymbols.pair(MoneyConstants.BASE_CURRENCY, currency);
        LocalDate from = curve.get(0).getDate();
        priceHistoryService.ensureCoverage(pair, from.minusDays(7));
        java.util.NavigableMap<LocalDate, DailyPrice> fx = priceHistoryService
                .loadSeries(List.of(pair), from.minusDays(7), today).getOrDefault(pair, new java.util.TreeMap<>());
        BigDecimal current = exchangeRateService.getRate(MoneyConstants.BASE_CURRENCY, currency);
        return curve.stream().map(p -> {
            Map.Entry<LocalDate, DailyPrice> entry = p.getDate().isBefore(today) ? fx.floorEntry(p.getDate()) : null;
            BigDecimal rate = entry != null ? entry.getValue().price() : current;
            return CurvePointDTO.builder()
                    .date(p.getDate())
                    .totalValueEur(scale(p.getTotalValueEur(), rate))
                    .totalInvestedEur(scale(p.getTotalInvestedEur(), rate))
                    .gainLossEur(scale(p.getGainLossEur(), rate))
                    .gainLossPercentage(p.getGainLossPercentage())
                    .build();
        }).toList();
    }

    private static BigDecimal scale(BigDecimal amount, BigDecimal rate) {
        return amount == null ? null
                : amount.multiply(rate).setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
    }

    // ------------------------------------------------------------- courbe

    private List<CurvePointDTO> buildCurveForUser(UUID userId, DashboardPeriod period) {
        LocalDate today = LocalDate.now();
        LocalDate start = period.startDate(today);

        List<PortfolioSnapshot> snapshots = (start == null)
                ? snapshotRepository.findAllByUserIdOrderBySnapshotDateAsc(userId)
                : snapshotRepository.findByUserIdAndSnapshotDateGreaterThanEqual(userId, start);

        return aggregateSnapshotsByDate(snapshots);
    }

    private List<CurvePointDTO> buildCurveForPortfolio(UUID portfolioId, DashboardPeriod period) {
        LocalDate today = LocalDate.now();
        LocalDate start = period.startDate(today);

        List<PortfolioSnapshot> snapshots = (start == null)
                ? snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId)
                : snapshotRepository.findByPortfolioIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
                        portfolioId, start);

        return snapshots.stream()
                .map(s -> CurvePointDTO.builder()
                        .date(s.getSnapshotDate())
                        .totalValueEur(s.getTotalValue())
                        .totalInvestedEur(s.getTotalInvested())
                        .gainLossEur(s.getGainLoss())
                        .gainLossPercentage(s.getGainLossPercentage())
                        .build())
                .toList();
    }

    /** Plusieurs portefeuilles peuvent avoir un snapshot le même jour : on agrège. */
    private List<CurvePointDTO> aggregateSnapshotsByDate(List<PortfolioSnapshot> snapshots) {
        Map<LocalDate, List<PortfolioSnapshot>> byDate = snapshots.stream()
                .collect(Collectors.groupingBy(PortfolioSnapshot::getSnapshotDate));

        return byDate.entrySet().stream()
                .map(e -> {
                    BigDecimal value = sum(e.getValue(), PortfolioSnapshot::getTotalValue);
                    BigDecimal invested = sum(e.getValue(), PortfolioSnapshot::getTotalInvested);
                    BigDecimal gain = value.subtract(invested);
                    BigDecimal pct = invested.signum() == 0
                            ? BigDecimal.ZERO
                            : gain.multiply(BigDecimal.valueOf(100))
                                    .divide(invested, MoneyConstants.PERCENT_SCALE, MoneyConstants.ROUNDING);

                    return CurvePointDTO.builder()
                            .date(e.getKey())
                            .totalValueEur(value)
                            .totalInvestedEur(invested)
                            .gainLossEur(gain)
                            .gainLossPercentage(pct)
                            .build();
                })
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .toList();
    }

    private BigDecimal sum(List<PortfolioSnapshot> list,
                            java.util.function.Function<PortfolioSnapshot, BigDecimal> getter) {
        return list.stream()
                .map(getter)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
    }

    // ------------------------------------------------------------- assemblage

    private DashboardResponse toResponse(ValuationResult v, List<CurvePointDTO> curve, String curveCurrency) {
        return DashboardResponse.builder()
                .totalValueEur(v.getTotalValueEur())
                .totalInvestedEur(v.getTotalInvestedEur())
                .unrealizedGainEur(v.getTotalUnrealizedGainEur())
                .unrealizedGainPercentage(v.getTotalUnrealizedGainPercentage())
                .realizedGainEur(v.getTotalRealizedGainEur())
                .dividendsEur(v.getTotalDividendsEur())
                .totalFeesEur(v.getTotalFeesEur())
                .interestEur(v.getTotalInterestEur())
                .cashEur(v.getTotalCashEur())
                .netDepositsEur(v.getTotalNetDepositsEur())
                .hasIncompletePrices(v.isHasIncompletePrices())
                .portfolios(v.getPortfolios())
                .allocation(buildAllocation(v))
                .curve(curve)
                .curveCurrency(curveCurrency)
                .build();
    }

    /**
     * Répartition par catégorie : type d'actif pour les positions, LIVRET pour
     * le solde des livrets, LIQUIDITES pour celui des autres comptes suivis.
     * Un solde négatif (versements non saisis) n'est pas une part : ignoré.
     */
    private List<AllocationSliceDTO> buildAllocation(ValuationResult v) {
        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (PortfolioValuation p : v.getPortfolios()) {
            for (PositionValuation position : p.getPositions()) {
                byCategory.merge(position.getAssetType().name(), position.getCurrentValueEur(), BigDecimal::add);
            }
            if (p.getCashEur() != null && p.getCashEur().signum() > 0) {
                String category = PortfolioRules.holdsOnlyCash(p.getType())
                        ? PortfolioType.LIVRET.name()
                        : AllocationSliceDTO.CASH;
                byCategory.merge(category, p.getCashEur(), BigDecimal::add);
            }
        }

        BigDecimal total = byCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return byCategory.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .map(e -> AllocationSliceDTO.builder()
                        .category(e.getKey())
                        .value(e.getValue().setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING))
                        .percentage(total.signum() == 0
                                ? BigDecimal.ZERO
                                : e.getValue().multiply(BigDecimal.valueOf(100))
                                        .divide(total, MoneyConstants.PERCENT_SCALE, MoneyConstants.ROUNDING))
                        .build())
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .toList();
    }
}
