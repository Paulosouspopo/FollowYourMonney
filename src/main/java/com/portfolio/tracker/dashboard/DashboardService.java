package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.dashboard.dto.*;
import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.snapshot.PortfolioSnapshot;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.portfolio.tracker.dashboard.PortfolioValuationService.percentage;
import static com.portfolio.tracker.dashboard.PortfolioValuationService.scaleMoney;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardService {

        private static final int RECENT_TRANSACTIONS_LIMIT = 10;
        private static final int EVOLUTION_DAYS = 90;
        private static final String DEFAULT_CURRENCY = "EUR";

        private final UserRepository userRepository;
        private final PortfolioRepository portfolioRepository;
        private final TransactionRepository transactionRepository;
        private final PortfolioSnapshotRepository snapshotRepository;
        private final PortfolioValuationService valuationService;

        public DashboardSummaryDTO getUserDashboard(UUID userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

                String baseCurrency = resolveBaseCurrency(user);
                List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

                List<PortfolioSnapshotDTO> snapshots = portfolios.stream()
                                .map(p -> valuationService.valuate(p, baseCurrency))
                                .toList();

                BigDecimal totalValue = sum(snapshots, PortfolioSnapshotDTO::getCurrentValue);
                BigDecimal totalInvested = sum(snapshots, PortfolioSnapshotDTO::getInvestedAmount);
                BigDecimal totalGainLoss = sum(snapshots, PortfolioSnapshotDTO::getGainLoss);

                return DashboardSummaryDTO.builder()
                                .totalValue(scaleMoney(totalValue))
                                .totalInvested(scaleMoney(totalInvested))
                                .totalGainLoss(scaleMoney(totalGainLoss))
                                .gainLossPercentage(percentage(totalGainLoss, totalInvested))
                                .currency(baseCurrency)
                                .portfolios(snapshots)
                                .allocation(buildAllocation(snapshots, totalValue))
                                .recentTransactions(getRecentTransactions(userId))
                                .evolutionCurve(getEvolutionCurve(userId))
                                .build();
        }

        /** Allocation par type d'actif, tous portefeuilles confondus. */
        private List<AllocationSliceDTO> buildAllocation(List<PortfolioSnapshotDTO> snapshots,
                        BigDecimal totalValue) {
                Map<AssetType, BigDecimal> byType = new EnumMap<>(AssetType.class);

                snapshots.stream()
                                .flatMap(s -> s.getAssets().stream())
                                .forEach(a -> byType.merge(a.getAssetType(), a.getCurrentValue(), BigDecimal::add));

                return byType.entrySet().stream()
                                .filter(e -> e.getValue().signum() != 0)
                                .map(e -> AllocationSliceDTO.builder()
                                                .assetType(e.getKey())
                                                .label(e.getKey().name())
                                                .value(scaleMoney(e.getValue()))
                                                .percentage(percentage(e.getValue(), totalValue))
                                                .build())
                                .sorted(Comparator.comparing(AllocationSliceDTO::getValue).reversed())
                                .toList();
        }

        private List<RecentTransactionDTO> getRecentTransactions(UUID userId) {
                return transactionRepository
                                .findRecentByUserId(userId, PageRequest.of(0, RECENT_TRANSACTIONS_LIMIT))
                                .stream()
                                .map(this::toRecentDto)
                                .toList();
        }

        private RecentTransactionDTO toRecentDto(Transaction tx) {
                return RecentTransactionDTO.builder()
                                .transactionId(tx.getId())
                                .assetSymbol(tx.getAsset().getSymbol())
                                .assetName(tx.getAsset().getName())
                                .type(tx.getType().name())
                                .quantity(tx.getQuantity())
                                .pricePerUnit(tx.getPricePerUnit())
                                .totalAmount(tx.getTotalAmount())
                                .currency(tx.getCurrency())
                                .transactionDate(tx.getTransactionDate())
                                .notes(tx.getNotes())
                                .build();
        }

        /**
         * Courbe d'évolution reconstruite depuis les snapshots quotidiens.
         * Les portefeuilles sont agrégés par date.
         */
        private List<EvolutionPointDTO> getEvolutionCurve(UUID userId) {
                LocalDate from = LocalDate.now().minusDays(EVOLUTION_DAYS);

                Map<LocalDate, List<PortfolioSnapshot>> byDate = snapshotRepository
                                .findByUserIdSince(userId, from)
                                .stream()
                                .collect(Collectors.groupingBy(PortfolioSnapshot::getSnapshotDate, TreeMap::new,
                                                Collectors.toList()));

                return byDate.entrySet().stream()
                                .map(entry -> {
                                        BigDecimal value = entry.getValue().stream()
                                                        .map(PortfolioSnapshot::getTotalValue)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                        BigDecimal invested = entry.getValue().stream()
                                                        .map(PortfolioSnapshot::getTotalInvested)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                        BigDecimal gain = value.subtract(invested);

                                        return EvolutionPointDTO.builder()
                                                        .date(entry.getKey())
                                                        .totalValue(scaleMoney(value))
                                                        .investedAmount(scaleMoney(invested))
                                                        .gainLoss(scaleMoney(gain))
                                                        .gainLossPercentage(percentage(gain, invested))
                                                        .build();
                                })
                                .toList();
        }

        private String resolveBaseCurrency(User user) {
                String currency = user.getPreferredCurrency();
                return (currency == null || currency.isBlank()) ? DEFAULT_CURRENCY : currency;
        }

        private BigDecimal sum(List<PortfolioSnapshotDTO> list,
                        java.util.function.Function<PortfolioSnapshotDTO, BigDecimal> extractor) {
                return list.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
}