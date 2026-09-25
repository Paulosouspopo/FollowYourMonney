package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.asset.ManualAssets;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.notification.alert.AlertRuleRepository;
import com.portfolio.tracker.watchlist.WatchlistRepository;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

/**
 * Cotations courantes (job horaire / rafraîchissement manuel) et lectures
 * exposées par l'API. L'historique est géré par {@link PriceHistoryService}.
 */
@Service
@AllArgsConstructor
@Slf4j
public class AssetPriceService {

    private static final int MAX_LIMIT = 1000;

    private final AssetPriceRepository assetPriceRepository;
    private final AssetRepository assetRepository;
    private final MarketDataProvider marketDataProvider;
    private final PriceHistoryService priceHistoryService;
    private final WatchlistRepository watchlistRepository;
    private final AlertRuleRepository alertRuleRepository;

    /** Rafraîchit la cotation du jour des symboles détenus, suivis ou surveillés par une alerte. */
    public void updateAllAssetPrices() {
        log.info("========== Starting asset price update ==========");

        try {
            Set<String> distinctSymbols = new LinkedHashSet<>(assetRepository.findAllDistinctSymbols());
            distinctSymbols.addAll(watchlistRepository.findAllDistinctSymbols());
            distinctSymbols.addAll(alertRuleRepository.findDistinctAssetSymbols());
            distinctSymbols.removeIf(ManualAssets::isManual); // valeurs saisies, pas de cotation

            if (distinctSymbols.isEmpty()) {
                log.info("No assets found to update");
                return;
            }

            int successCount = 0;
            int errorCount = 0;

            for (String symbol : distinctSymbols) {
                if (updatePriceForSymbol(symbol)) {
                    successCount++;
                } else {
                    errorCount++;
                }
            }

            log.info("========== Asset price update completed: success={}, errors={} ==========",
                    successCount, errorCount);

        } catch (Exception e) {
            log.error("Error during asset price update", e);
        }
    }

    private boolean updatePriceForSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("updatePriceForSymbol called with invalid symbol");
            return false;
        }

        // Vide pour un actif seulement suivi (watchlist, alerte) : la cotation est quand même enregistrée
        List<Asset> assets = assetRepository.findAllBySymbol(symbol);

        Optional<MarketQuote> quote = marketDataProvider.getQuote(symbol);
        if (quote.isEmpty()) {
            log.warn("Failed to retrieve price for symbol: {}", symbol);
            return false;
        }

        MarketQuote q = quote.get();
        priceHistoryService.saveQuote(symbol, q);

        // On enrichit tous les assets partageant ce symbole (un par portefeuille)
        assets.forEach(asset -> enrichAssetMetadataIfNeeded(asset, q));

        log.debug("Price updated for {}: {} {} ({})", symbol, q.price(), q.currency(), q.marketDate());
        return true;
    }

    private void enrichAssetMetadataIfNeeded(Asset asset, MarketQuote quote) {
        boolean changed = false;

        if (asset.getLongName() == null && quote.longName() != null) {
            asset.setLongName(quote.longName());
            changed = true;
        }
        if (asset.getExchangeName() == null && quote.exchangeName() != null) {
            asset.setExchangeName(quote.exchangeName());
            changed = true;
        }

        if (changed) {
            try {
                assetRepository.save(asset);
                log.debug("Asset metadata enriched for symbol: {}", quote.symbol());
            } catch (Exception e) {
                log.error("Error enriching asset metadata for symbol {}: {}", quote.symbol(), e.getMessage());
            }
        }
    }

    public Optional<AssetPrice> getLatestPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("getLatestPrice called with invalid symbol");
            return Optional.empty();
        }
        return assetPriceRepository.findTopBySymbolOrderByPriceDateDesc(symbol);
    }

    public List<AssetPrice> getLatestPrices(String symbol, int limit) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("getLatestPrices called with invalid symbol");
            return List.of();
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            log.warn("getLatestPrices called with invalid limit: {}", limit);
            return List.of();
        }
        return assetPriceRepository.findLatestPricesBySymbol(symbol, limit);
    }

    public List<AssetPrice> getPricesForPeriod(String symbol, LocalDateTime startDate, LocalDateTime endDate) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("getPricesForPeriod called with invalid symbol");
            return List.of();
        }
        if (startDate == null || endDate == null) {
            log.warn("getPricesForPeriod called with null dates");
            return List.of();
        }
        if (startDate.isAfter(endDate)) {
            log.warn("getPricesForPeriod: startDate is after endDate");
            return List.of();
        }
        return assetPriceRepository.findBySymbolAndLastUpdatedBetweenOrderByLastUpdatedAsc(symbol, startDate, endDate);
    }

    public boolean refreshPriceForSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("refreshPriceForSymbol called with invalid symbol");
            return false;
        }
        log.info("Manual refresh requested for symbol: {}", symbol);
        return updatePriceForSymbol(symbol);
    }

    public Map<String, Long> getPricesCountBySymbol() {
        try {
            List<String> symbols = assetRepository.findAllDistinctSymbols();
            Map<String, Long> stats = new java.util.HashMap<>();
            for (String symbol : symbols) {
                stats.put(symbol, assetPriceRepository.countBySymbol(symbol));
            }
            return stats;
        } catch (Exception e) {
            log.error("Error retrieving prices count by symbol", e);
            return Map.of();
        }
    }
}
