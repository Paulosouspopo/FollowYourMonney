package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class AssetPriceService {

    private static final String SOURCE_YAHOO = "YAHOO_FINANCE";
    private static final int MAX_LIMIT = 1000;

    private final AssetPriceRepository assetPriceRepository;
    private final AssetRepository assetRepository;
    private final MarketDataProvider marketDataProvider;

    @Scheduled(cron = "${asset.price.update.cron:0 0 * * * *}")
    public void updateAllAssetPrices() {
        log.info("========== Starting scheduled asset price update ==========");

        try {
            List<String> distinctSymbols = assetRepository.findAllDistinctSymbols();

            if (distinctSymbols.isEmpty()) {
                log.info("No assets found to update");
                return;
            }

            log.info("Found {} distinct symbols to update", distinctSymbols.size());

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
            log.error("Error during scheduled asset price update", e);
        }
    }

    private boolean updatePriceForSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("updatePriceForSymbol called with invalid symbol");
            return false;
        }

        Asset asset = assetRepository.findBySymbol(symbol);
        if (asset == null) {
            log.warn("No asset found for symbol: {}", symbol);
            return false;
        }

        Optional<MarketQuote> quote = marketDataProvider.getQuote(symbol);

        if (quote.isEmpty()) {
            log.warn("Failed to retrieve price for symbol: {}", symbol);
            return false;
        }

        MarketQuote q = quote.get();
        saveAssetPrice(q);
        enrichAssetMetadataIfNeeded(asset, q);

        log.info("Price updated for {}: {} {}", q.symbol(), q.price(), q.currency());
        return true;
    }

    private void saveAssetPrice(MarketQuote quote) {
        if (quote == null) {
            log.warn("Attempted to save null MarketQuote");
            return;
        }

        try {
            AssetPrice assetPrice = AssetPrice.builder()
                    .symbol(quote.symbol())
                    .price(quote.price())
                    .currency(quote.currency())
                    .lastUpdated(quote.asOf())
                    .source(SOURCE_YAHOO)
                    .build();

            assetPriceRepository.save(assetPrice);
            log.debug("AssetPrice saved for symbol: {}", quote.symbol());
        } catch (Exception e) {
            log.error("Error saving asset price for symbol {}: {}", quote.symbol(), e.getMessage());
        }
    }

    private void enrichAssetMetadataIfNeeded(Asset asset, MarketQuote quote) {
        if (asset == null || quote == null) {
            log.warn("enrichAssetMetadataIfNeeded called with null parameters");
            return;
        }

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
        return assetPriceRepository.findLatestBySymbol(symbol);
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
        return assetPriceRepository.findBySymbolAndLastUpdatedBetween(symbol, startDate, endDate);
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
