package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.dashboard.dto.*;
import com.portfolio.tracker.snapshot.PortfolioSnapshot;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Dashboard global : tous les portefeuilles de l'utilisateur. */
    public DashboardResponse getDashboard(UUID userId, String periodCode) {
        DashboardPeriod period = DashboardPeriod.fromCode(periodCode);

        ValuationResult valuation = valuationService.valuate(userId, null, null);
        List<CurvePointDTO> curve = buildCurveForUser(userId, period);

        return toResponse(valuation, curve);
    }

    /**
     * Détail d'un portefeuille précis. Réutilise valuationService.valuate()
     * en filtrant sur portfolioId — quasi gratuit car même moteur de calcul.
     */
    public DashboardResponse getPortfolioDashboard(UUID userId, UUID portfolioId, String periodCode) {
        DashboardPeriod period = DashboardPeriod.fromCode(periodCode);

        ValuationResult valuation = valuationService.valuate(userId, portfolioId, null);
        if (valuation.getPortfolios().isEmpty()) {
            throw new ResourceNotFoundException("Portefeuille introuvable ou vide", portfolioId);
        }

        List<CurvePointDTO> curve = buildCurveForPortfolio(portfolioId, period);

        return toResponse(valuation, curve);
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

    private DashboardResponse toResponse(ValuationResult v, List<CurvePointDTO> curve) {
        return DashboardResponse.builder()
                .totalValueEur(v.getTotalValueEur())
                .totalInvestedEur(v.getTotalInvestedEur())
                .unrealizedGainEur(v.getTotalUnrealizedGainEur())
                .unrealizedGainPercentage(v.getTotalUnrealizedGainPercentage())
                .realizedGainEur(v.getTotalRealizedGainEur())
                .dividendsEur(v.getTotalDividendsEur())
                .totalFeesEur(v.getTotalFeesEur())
                .hasIncompletePrices(v.isHasIncompletePrices())
                .portfolios(v.getPortfolios())
                .allocation(buildAllocation(v))
                .curve(curve)
                .build();
    }

    private List<AllocationSliceDTO> buildAllocation(ValuationResult v) {
        Map<com.portfolio.tracker.asset.AssetType, BigDecimal> byType = v.getPortfolios().stream()
                .flatMap(p -> p.getPositions().stream())
                .collect(Collectors.groupingBy(
                        PositionValuation::getAssetType,
                        Collectors.reducing(BigDecimal.ZERO, PositionValuation::getCurrentValueEur, BigDecimal::add)));

        BigDecimal total = byType.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return byType.entrySet().stream()
                .map(e -> AllocationSliceDTO.builder()
                        .assetType(e.getKey())
                        .label(e.getKey().name())
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