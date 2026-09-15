package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.assetprice.AssetPrice;
import com.portfolio.tracker.assetprice.AssetPriceRepository;
import com.portfolio.tracker.dashboard.dto.AssetPerformanceDTO;
import com.portfolio.tracker.dashboard.dto.PortfolioSnapshotDTO;
import com.portfolio.tracker.exchangerate.ExchangeRateService;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortfolioValuationService {

    private static final int MONEY_SCALE = 2;
    private static final int QTY_SCALE = 8;
    private static final int PCT_SCALE = 4;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final ExchangeRateService exchangeRateService;

    /**
     * Valorise un portefeuille complet à l'instant T.
     * Les transactions sont chargées en une seule requête puis groupées en mémoire
     * pour éviter le N+1 (une requête par asset).
     */
    public PortfolioSnapshotDTO valuate(Portfolio portfolio, String baseCurrency) {
        UUID userId = portfolio.getUser().getId();

        List<Asset> assets = assetRepository.findByPortfolioIdAndUserId(portfolio.getId(), userId);
        if (assets.isEmpty()) {
            return emptySnapshot(portfolio, baseCurrency);
        }

        // Une seule requête pour toutes les transactions du portefeuille
        Map<UUID, List<Transaction>> txByAsset = transactionRepository
                .findByPortfolioIdAndUserId(portfolio.getId(), userId)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getAsset().getId()));

        // Une seule requête pour tous les derniers prix
        Set<String> symbols = assets.stream().map(Asset::getSymbol).collect(Collectors.toSet());
        Map<String, BigDecimal> latestPrices = loadLatestPrices(symbols);

        List<AssetPerformanceDTO> performances = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalRealized = BigDecimal.ZERO;

        for (Asset asset : assets) {
            AssetPerformanceDTO perf = valuateAsset(
                    asset,
                    txByAsset.getOrDefault(asset.getId(), List.of()),
                    latestPrices.get(asset.getSymbol()),
                    baseCurrency
            );

            // On n'affiche pas les positions totalement soldées
            if (perf.getQuantity().signum() == 0 && perf.getRealizedGainLoss().signum() == 0) {
                continue;
            }

            performances.add(perf);
            totalValue = totalValue.add(perf.getCurrentValue());
            totalInvested = totalInvested.add(perf.getInvestedAmount());
            totalRealized = totalRealized.add(perf.getRealizedGainLoss());
        }

        BigDecimal unrealized = totalValue.subtract(totalInvested);
        BigDecimal gainLoss = unrealized.add(totalRealized);

        return PortfolioSnapshotDTO.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getName())
                .portfolioType(portfolio.getType())
                .currentValue(scaleMoney(totalValue))
                .investedAmount(scaleMoney(totalInvested))
                .gainLoss(scaleMoney(gainLoss))
                .gainLossPercentage(percentage(gainLoss, totalInvested))
                .assetCount(performances.size())
                .assets(performances)
                .currency(baseCurrency)
                .build();
    }

    /**
     * Valorise une position avec la méthode du coût moyen pondéré (CUMP).
     * Les ventes réduisent le coût de revient au prorata, et dégagent
     * une plus/moins-value réalisée.
     */
    private AssetPerformanceDTO valuateAsset(Asset asset,
                                             List<Transaction> transactions,
                                             BigDecimal rawPrice,
                                             String baseCurrency) {

        List<Transaction> ordered = transactions.stream()
                .sorted(Comparator.comparing(
                        Transaction::getTransactionDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        BigDecimal quantity = BigDecimal.ZERO;   // quantité détenue
        BigDecimal costBasis = BigDecimal.ZERO;  // coût de revient en devise de base
        BigDecimal realized = BigDecimal.ZERO;   // P/L réalisée
        BigDecimal dividends = BigDecimal.ZERO;

        for (Transaction tx : ordered) {
            BigDecimal amountInBase = amountInBaseCurrency(tx);

            switch (tx.getType()) {
                case BUY -> {
                    quantity = quantity.add(tx.getQuantity());
                    // Les frais font partie du prix de revient
                    costBasis = costBasis.add(amountInBase);
                }
                case SELL -> {
                    if (quantity.signum() <= 0) {
                        log.warn("Vente sans position ouverte — asset={}, tx={}",
                                asset.getSymbol(), tx.getId());
                        continue;
                    }
                    BigDecimal sold = tx.getQuantity().min(quantity);
                    // Part du coût de revient sortie du portefeuille
                    BigDecimal costOut = costBasis
                            .multiply(sold)
                            .divide(quantity, MONEY_SCALE, RM);

                    realized = realized.add(amountInBase.subtract(costOut));
                    costBasis = costBasis.subtract(costOut);
                    quantity = quantity.subtract(sold);
                }
                case DIVIDEND -> dividends = dividends.add(amountInBase);
            }
        }

        realized = realized.add(dividends);

        BigDecimal priceInBase = convertPrice(rawPrice, asset, baseCurrency);
        BigDecimal currentValue = quantity.multiply(priceInBase);
        BigDecimal unrealized = currentValue.subtract(costBasis);

        BigDecimal avgCost = quantity.signum() > 0
                ? costBasis.divide(quantity, QTY_SCALE, RM)
                : BigDecimal.ZERO;

        return AssetPerformanceDTO.builder()
                .assetId(asset.getId())
                .symbol(asset.getSymbol())
                .name(asset.getLongName() != null ? asset.getLongName() : asset.getName())
                .assetType(asset.getAssetType())
                .currentPrice(priceInBase)
                .quantity(quantity.stripTrailingZeros())
                .currentValue(scaleMoney(currentValue))
                .averageCostPerUnit(avgCost)
                .investedAmount(scaleMoney(costBasis))
                .gainLoss(scaleMoney(unrealized))
                .gainLossPercentage(percentage(unrealized, costBasis))
                .realizedGainLoss(scaleMoney(realized))
                .priceAvailable(rawPrice != null)
                .currency(baseCurrency)
                .build();
    }

    /**
     * Montant de la transaction dans la devise de référence.
     * On privilégie le taux figé au moment de l'opération : une conversion
     * au taux du jour ferait varier rétroactivement le montant investi.
     */
    private BigDecimal amountInBaseCurrency(Transaction tx) {
        BigDecimal gross = tx.getTotalAmount() != null ? tx.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal fees = tx.getFees() != null ? tx.getFees() : BigDecimal.ZERO;

        // Les frais alourdissent un achat et réduisent le produit d'une vente
        BigDecimal net = tx.getType() == TransactionType.SELL
                ? gross.subtract(fees)
                : gross.add(fees);

        BigDecimal rate = tx.getExchangeRate() != null && tx.getExchangeRate().signum() > 0
                ? tx.getExchangeRate()
                : BigDecimal.ONE;

        return net.multiply(rate);
    }

    private BigDecimal convertPrice(BigDecimal rawPrice, Asset asset, String baseCurrency) {
        if (rawPrice == null) {
            return BigDecimal.ZERO;
        }
        String from = asset.getCurrency();
        if (from == null || from.equalsIgnoreCase(baseCurrency)) {
            return rawPrice;
        }
        try {
            return rawPrice.multiply(exchangeRateService.getRate(from, baseCurrency));
        } catch (Exception e) {
            log.error("Conversion {}->{} impossible pour {} : {}",
                    from, baseCurrency, asset.getSymbol(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** Charge le dernier prix connu de chaque symbole. Un prix manquant vaut null, pas une exception. */
    private Map<String, BigDecimal> loadLatestPrices(Set<String> symbols) {
        Map<String, BigDecimal> prices = new HashMap<>();
        for (String symbol : symbols) {
            assetPriceRepository.findLatestBySymbol(symbol)
                    .map(AssetPrice::getPrice)
                    .ifPresentOrElse(
                            p -> prices.put(symbol, p),
                            () -> log.warn("Aucun prix disponible pour le symbole {}", symbol)
                    );
        }
        return prices;
    }

    private PortfolioSnapshotDTO emptySnapshot(Portfolio portfolio, String baseCurrency) {
        return PortfolioSnapshotDTO.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getName())
                .portfolioType(portfolio.getType())
                .currentValue(BigDecimal.ZERO)
                .investedAmount(BigDecimal.ZERO)
                .gainLoss(BigDecimal.ZERO)
                .gainLossPercentage(BigDecimal.ZERO)
                .assetCount(0)
                .assets(List.of())
                .currency(baseCurrency)
                .build();
    }

    static BigDecimal scaleMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(MONEY_SCALE, RM);
    }

    static BigDecimal percentage(BigDecimal gain, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return gain.multiply(BigDecimal.valueOf(100))
                .divide(base, PCT_SCALE, RM);
    }
}