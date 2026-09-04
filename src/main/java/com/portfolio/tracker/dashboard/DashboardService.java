package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.assetprice.AssetPrice;
import com.portfolio.tracker.assetprice.AssetPriceRepository;
import com.portfolio.tracker.dashboard.dto.*;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final AssetPriceRepository assetPriceRepository;

    /**
     * Récupère le dashboard global pour un utilisateur
     */
    public DashboardSummaryDTO getUserDashboard(UUID userId) {
        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);
        
        List<PortfolioSnapshotDTO> portfolioSnapshots = portfolios.stream()
                .map(portfolio -> buildPortfolioSnapshot(portfolio, userId))
                .collect(Collectors.toList());

        // Calculs globaux
        BigDecimal totalValue = portfolioSnapshots.stream()
                .map(PortfolioSnapshotDTO::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInvested = portfolioSnapshots.stream()
                .map(PortfolioSnapshotDTO::getInvestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGainLoss = totalValue.subtract(totalInvested);
        BigDecimal gainLossPercentage = calculatePercentage(totalGainLoss, totalInvested);

        // Allocation globale
        List<AllocationSliceDTO> allocation = calculateAllocation(portfolios, userId);

        // Transactions récentes
        List<RecentTransactionDTO> recentTransactions = getRecentTransactions(userId, 10);

        // Courbe d'évolution (pour le dernier mois, par exemple)
        List<EvolutionPointDTO> evolutionCurve = calculateEvolutionCurve(portfolios, 30, userId);

        return DashboardSummaryDTO.builder()
                .totalValue(totalValue)
                .totalInvested(totalInvested)
                .totalGainLoss(totalGainLoss)
                .gainLossPercentage(gainLossPercentage)
                .portfolios(portfolioSnapshots)
                .allocation(allocation)
                .recentTransactions(recentTransactions)
                .evolutionCurve(evolutionCurve)
                .build();
    }

    /**
     * Construit le snapshot d'un portefeuille spécifique
     */
    private PortfolioSnapshotDTO buildPortfolioSnapshot(Portfolio portfolio, UUID userId) {
        List<Asset> assets = assetRepository.findByPortfolioIdAndUserId(portfolio.getId(), userId);

        List<AssetPerformanceDTO> assetPerformances = assets.stream()
                .map(asset -> buildAssetPerformance(asset, userId))
                .collect(Collectors.toList());

        BigDecimal currentValue = assetPerformances.stream()
                .map(AssetPerformanceDTO::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal investedAmount = assetPerformances.stream()
                .map(AssetPerformanceDTO::getInvestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gainLoss = currentValue.subtract(investedAmount);
        BigDecimal gainLossPercentage = calculatePercentage(gainLoss, investedAmount);

        return PortfolioSnapshotDTO.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .type(portfolio.getType().name())
                .currentValue(currentValue)
                .investedAmount(investedAmount)
                .gainLoss(gainLoss)
                .gainLossPercentage(gainLossPercentage)
                .assetCount(assets.size())
                .assets(assetPerformances)
                .build();
    }

    /**
     * Construit les performances détaillées d'un actif
     */
    private AssetPerformanceDTO buildAssetPerformance(Asset asset, UUID userId) {
        List<Transaction> transactions = transactionRepository.findByAssetIdAndUserId(asset.getId(), userId);

        // Quantité totale détenue = BUY - SELL
        BigDecimal quantity = transactions.stream()
                .filter(t -> t.getType() == TransactionType.BUY)
                .map(Transaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(
                    transactions.stream()
                        .filter(t -> t.getType() == TransactionType.SELL)
                        .map(Transaction::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                );

        // Montant investi = somme des (quantity * pricePerUnit) pour les BUY
        // moins la récupération des SELL (quantity * pricePerUnit)
        BigDecimal investedOnBuy = transactions.stream()
                .filter(t -> t.getType() == TransactionType.BUY)
                .map(t -> t.getQuantity().multiply(t.getPricePerUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal investedOnSell = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .map(t -> t.getQuantity().multiply(t.getPricePerUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal investedAmount = investedOnBuy.subtract(investedOnSell);

        // Prix moyen d'achat
        BigDecimal quantityBuy = transactions.stream()
                .filter(t -> t.getType() == TransactionType.BUY)
                .map(Transaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageCostPerUnit = BigDecimal.ZERO;
        if (quantityBuy.compareTo(BigDecimal.ZERO) > 0) {
            averageCostPerUnit = investedOnBuy.divide(quantityBuy, 8, RoundingMode.HALF_UP);
        }

        // Prix actuel depuis AssetPrice
        AssetPrice latestPrice = assetPriceRepository.findTopBySymbolOrderByLastUpdatedDesc(asset.getSymbol())
                .orElse(null);

        BigDecimal currentPrice = latestPrice != null ? latestPrice.getPrice() : BigDecimal.ZERO;
        BigDecimal currentValue = quantity.multiply(currentPrice);

        // Gain/perte non-réalisé
        BigDecimal gainLoss = currentValue.subtract(investedAmount);
        BigDecimal gainLossPercentage = calculatePercentage(gainLoss, investedAmount);

        return AssetPerformanceDTO.builder()
                .assetId(asset.getId())
                .symbol(asset.getSymbol())
                .name(asset.getName())
                .assetType(asset.getAssetType().name())
                .currentPrice(currentPrice)
                .quantity(quantity)
                .currentValue(currentValue)
                .averageCostPerUnit(averageCostPerUnit)
                .investedAmount(investedAmount)
                .gainLoss(gainLoss)
                .gainLossPercentage(gainLossPercentage)
                .currency(asset.getCurrency() != null ? asset.getCurrency() : "EUR")
                .build();
    }

    /**
     * Calcule l'allocation globale par type d'actif
     */
    private List<AllocationSliceDTO> calculateAllocation(List<Portfolio> portfolios, UUID userId) {
        Map<String, BigDecimal> allocationMap = new HashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            List<Asset> assets = assetRepository.findByPortfolioIdAndUserId(portfolio.getId(), userId);
            for (Asset asset : assets) {
                AssetPerformanceDTO perf = buildAssetPerformance(asset, userId);
                String assetType = perf.getAssetType();
                
                allocationMap.put(
                    assetType,
                    allocationMap.getOrDefault(assetType, BigDecimal.ZERO)
                            .add(perf.getCurrentValue())
                );
                totalValue = totalValue.add(perf.getCurrentValue());
            }
        }

        BigDecimal finalTotalValue = totalValue;
        return allocationMap.entrySet().stream()
                .map(entry -> {
                    BigDecimal value = entry.getValue();
                    BigDecimal percentage = finalTotalValue.compareTo(BigDecimal.ZERO) > 0
                            ? value.divide(finalTotalValue, 2, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                            : BigDecimal.ZERO;

                    return AllocationSliceDTO.builder()
                            .assetType(entry.getKey())
                            .value(value)
                            .percentage(percentage)
                            .count((int) portfolios.stream()
                                    .flatMap(p -> assetRepository.findByPortfolioIdAndUserId(p.getId(), userId).stream())
                                    .filter(a -> a.getAssetType().name().equals(entry.getKey()))
                                    .count())
                            .build();
                })
                .sorted(Comparator.comparing(AllocationSliceDTO::getValue).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Récupère les N dernières transactions
     */
    private List<RecentTransactionDTO> getRecentTransactions(UUID userId, int limit) {
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByTransactionDateDesc(userId);

        return transactions.stream()
                .limit(limit)
                .map(t -> RecentTransactionDTO.builder()
                        .transactionId(t.getId())
                        .assetSymbol(t.getAsset().getSymbol())
                        .assetName(t.getAsset().getName())
                        .type(t.getType().name())
                        .quantity(t.getQuantity())
                        .pricePerUnit(t.getPricePerUnit())
                        .totalAmount(t.getTotalAmount())
                        .currency(t.getCurrency() != null ? t.getCurrency() : "EUR")
                        .transactionDate(t.getTransactionDate())
                        .notes(t.getNotes())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Calcule la courbe d'évolution du portefeuille sur les N derniers jours
     * (approximatif : basé sur les transactions historiques)
     */
    private List<EvolutionPointDTO> calculateEvolutionCurve(List<Portfolio> portfolios, int lastDays, UUID userId) {
        Map<LocalDateTime, BigDecimal> evolutionMap = new TreeMap<>();

        // Récupère toutes les transactions
        List<Transaction> allTransactions = portfolios.stream()
                .flatMap(p -> assetRepository.findByPortfolioIdAndUserId(p.getId(), userId).stream())
                .flatMap(a -> transactionRepository.findByAssetIdAndUserId(a.getId(), userId).stream())
                .filter(t -> t.getTransactionDate() != null)
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .collect(Collectors.toList());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(lastDays);

        // Pour chaque jour, calcule la valeur du portefeuille
        for (int i = 0; i < lastDays; i++) {
            LocalDateTime datePoint = startDate.plusDays(i);
            
            // Calcule les positions à cette date
            BigDecimal portfolioValue = BigDecimal.ZERO;
            BigDecimal investedAtDate = BigDecimal.ZERO;

            // Agrège les transactions jusqu'à cette date
            Map<UUID, BigDecimal> quantityByAsset = new HashMap<>();
            Map<UUID, BigDecimal> investedByAsset = new HashMap<>();
            Map<UUID, Asset> assetById = new HashMap<>();

            for (Transaction t : allTransactions) {
                if (t.getTransactionDate().isBefore(datePoint)) {
                    UUID assetId = t.getAsset().getId();
                    assetById.put(assetId, t.getAsset());

                    if (t.getType() == TransactionType.BUY) {
                        quantityByAsset.put(assetId, 
                            quantityByAsset.getOrDefault(assetId, BigDecimal.ZERO).add(t.getQuantity()));
                        investedByAsset.put(assetId,
                            investedByAsset.getOrDefault(assetId, BigDecimal.ZERO)
                                    .add(t.getQuantity().multiply(t.getPricePerUnit())));
                    } else if (t.getType() == TransactionType.SELL) {
                        quantityByAsset.put(assetId,
                            quantityByAsset.getOrDefault(assetId, BigDecimal.ZERO).subtract(t.getQuantity()));
                        investedByAsset.put(assetId,
                            investedByAsset.getOrDefault(assetId, BigDecimal.ZERO)
                                    .subtract(t.getQuantity().multiply(t.getPricePerUnit())));
                    }
                }
            }

            // Calcule la valeur actuelle à cette date
            for (Map.Entry<UUID, BigDecimal> entry : quantityByAsset.entrySet()) {
                UUID assetId = entry.getKey();
                BigDecimal qty = entry.getValue();
                Asset asset = assetById.get(assetId);

                // Cherche le prix le plus proche avant cette date
                AssetPrice priceAtDate = assetPriceRepository
                        .findTopBySymbolAndLastUpdatedLessThanEqualOrderByLastUpdatedDesc(asset.getSymbol(), datePoint)
                        .orElse(null);

                if (priceAtDate != null) {
                    portfolioValue = portfolioValue.add(qty.multiply(priceAtDate.getPrice()));
                }

                investedAtDate = investedAtDate.add(investedByAsset.getOrDefault(assetId, BigDecimal.ZERO));
            }

            BigDecimal gainLoss = portfolioValue.subtract(investedAtDate);

            evolutionMap.put(datePoint, gainLoss);
        }

        return evolutionMap.entrySet().stream()
                .map(entry -> EvolutionPointDTO.builder()
                        .date(entry.getKey())
                        .gainLoss(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Utilitaire : calcule un pourcentage
     */
    private BigDecimal calculatePercentage(BigDecimal value, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(total, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
    }
}