package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.snapshot.BackfillService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class AssetPriceService {

    private static final String SOURCE_YAHOO = "YAHOO_FINANCE";
    private static final int MAX_LIMIT = 1000;

    private final AssetPriceRepository assetPriceRepository;
    private final AssetRepository assetRepository;
    private final MarketDataProvider marketDataProvider;
    private final BackfillService backfillService;

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

        List<Asset> assets = assetRepository.findAllBySymbol(symbol);
        if (assets.isEmpty()) {
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

        // On enrichit tous les assets partageant ce symbole (un par user
        // potentiellement)
        assets.forEach(asset -> enrichAssetMetadataIfNeeded(asset, q));

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
        return assetPriceRepository.findTopBySymbolOrderByLastUpdatedDesc(symbol);
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

    /**
     * Garantit qu'un symbole a un prix courant + un historique couvrant
     * au moins jusqu'à {@code oldestNeeded}. Idempotent, ne lève jamais.
     * Si un backfill a effectivement inséré des points, régénère les
     * snapshots des portefeuilles concernés pour que la courbe se mette à jour.
     */
    public void ensurePriceHistory(String symbol, LocalDateTime oldestNeeded) {
        if (symbol == null || symbol.isBlank())
            return;

        try {
            if (assetPriceRepository.countBySymbol(symbol) == 0) {
                updatePriceForSymbol(symbol);
            }

            LocalDate target = (oldestNeeded != null ? oldestNeeded : LocalDateTime.now().minusYears(1))
                    .toLocalDate();
            LocalDate earliest = assetPriceRepository
                    .findEarliestPriceDateBySymbol(symbol)
                    .map(LocalDateTime::toLocalDate)
                    .orElse(null);

            int inserted;
            if (earliest == null) {
                // Aucun historique du tout : on couvre [target, hier]
                inserted = backfillHistory(symbol, target, LocalDate.now().minusDays(1));
            } else if (earliest.isAfter(target)) {
                // Historique existant mais pas assez ancien : on ne recharge
                // QUE le trou [target, earliest - 1 jour], jamais ce qui existe déjà.
                inserted = backfillHistory(symbol, target, earliest.minusDays(1));
            } else {
                inserted = 0; // déjà couvert
            }

            if (inserted > 0) {
                backfillService.backfillPortfoliosForSymbol(symbol);
            }
        } catch (Exception e) {
            log.error("ensurePriceHistory failed for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Insère l'historique journalier Yahoo, en ignorant les jours déjà présents.
     */
    public int backfillHistory(String symbol, String range) {
        List<MarketPricePoint> points = marketDataProvider.getDailyHistory(symbol, range);
        if (points.isEmpty()) {
            log.warn("No history returned for {} (range={})", symbol, range);
            return 0;
        }

        LocalDateTime from = points.get(0).asOf().minusDays(1);
        LocalDateTime to = points.get(points.size() - 1).asOf().plusDays(1);

        Set<LocalDate> existingDays = assetPriceRepository
                .findBySymbolAndLastUpdatedBetweenOrderByLastUpdatedAsc(symbol, from, to).stream()
                .map(ap -> ap.getLastUpdated().toLocalDate())
                .collect(Collectors.toSet());

        List<AssetPrice> toInsert = points.stream()
                .filter(p -> !existingDays.contains(p.asOf().toLocalDate()))
                .map(p -> AssetPrice.builder()
                        .symbol(p.symbol())
                        .price(p.price())
                        .currency(p.currency())
                        .lastUpdated(p.asOf())
                        .source(SOURCE_YAHOO)
                        .build())
                .toList();

        try {
            assetPriceRepository.saveAll(toInsert);
        } catch (DataIntegrityViolationException e) {
            log.warn("Doublon ignoré lors du backfill de {} (course concurrente)", symbol);
        }
        log.info("Backfill {} (range={}): {} points reçus, {} insérés", symbol, range, points.size(), toInsert.size());
        return toInsert.size();
    }

    /** Plus petit range Yahoo couvrant la date demandée. */
    private static String rangeCovering(LocalDateTime target) {
        long days = ChronoUnit.DAYS.between(target, LocalDateTime.now());
        if (days <= 30)
            return "1mo";
        if (days <= 90)
            return "3mo";
        if (days <= 180)
            return "6mo";
        if (days <= 365)
            return "1y";
        if (days <= 730)
            return "2y";
        if (days <= 1825)
            return "5y";
        if (days <= 3650)
            return "10y";
        return "max";
    }

    /**
     * Insère l'historique Yahoo sur [from, to], en ignorant les jours déjà
     * présents. Idempotent.
     */
    public int backfillHistory(String symbol, LocalDate from, LocalDate to) {
        if (from.isAfter(to))
            return 0;

        List<MarketPricePoint> points = marketDataProvider.getDailyHistory(symbol, from, to);
        if (points.isEmpty()) {
            log.warn("No history returned for {} [{},{}]", symbol, from, to);
            return 0;
        }

        Set<LocalDate> existingDays = assetPriceRepository
                .findBySymbolAndLastUpdatedBetweenOrderByLastUpdatedAsc(
                        symbol, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .map(ap -> ap.getLastUpdated().toLocalDate())
                .collect(Collectors.toSet());

        List<AssetPrice> toInsert = points.stream()
                .filter(p -> !existingDays.contains(p.asOf().toLocalDate())) // <-- clé anti-doublon
                .map(p -> AssetPrice.builder()
                        .symbol(p.symbol())
                        .price(p.price())
                        .currency(p.currency())
                        .lastUpdated(p.asOf())
                        .source(SOURCE_YAHOO)
                        .build())
                .toList();

        try {
            assetPriceRepository.saveAll(toInsert);
        } catch (DataIntegrityViolationException e) {
            log.warn("Doublon ignoré lors du backfill de {} (course concurrente)", symbol);
        }
        log.info("Backfill {} [{},{}] : {} reçus, {} insérés (doublons ignorés={})",
                symbol, from, to, points.size(), toInsert.size(), points.size() - toInsert.size());
        return toInsert.size();
    }
}
