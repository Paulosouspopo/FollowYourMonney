package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.assetprice.client.AlphaVantageClient;
import com.portfolio.tracker.assetprice.client.CoinGeckoClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class AssetPriceService {

    private final AssetPriceRepository assetPriceRepository;
    private final AssetRepository assetRepository;
    private final AlphaVantageClient alphaVantageClient;
    private final CoinGeckoClient coinGeckoClient;

    /**
     * ⭐ SCHEDULER : Récupère tous les symboles distincts et met à jour leur prix
     * Une seule fois par heure pour tous les actifs
     */
    @Scheduled(cron = "${asset.price.update.cron:0 0 * * * *}")
    public void updateAllAssetPrices() {
        log.info("========== Starting scheduled asset price update ==========");

        try {
            // 1️⃣ Récupérer tous les SYMBOLES DISTINCTS
            List<String> distinctSymbols = assetRepository.findAllDistinctSymbols();

            if (distinctSymbols.isEmpty()) {
                log.info("No assets found to update");
                return;
            }

            log.info("Found {} distinct symbols to update", distinctSymbols.size());

            int successCount = 0;
            int errorCount = 0;

            // 2️⃣ Pour CHAQUE symbole distinct
            for (String symbol : distinctSymbols) {
                if (updatePriceForSymbol(symbol)) {
                    successCount++;
                } else {
                    errorCount++;
                }
            }

            log.info("========== Asset price update completed ==========");
            log.info("Success: {}, Errors: {}", successCount, errorCount);

        } catch (Exception e) {
            log.error("Error during scheduled asset price update: {}", e.getMessage(), e);
        }
    }

    /**
     * ⭐ Met à jour le prix d'UN SYMBOLE DISTINCT
     * Récupère le prix une fois via l'API appropriée
     * Puis l'enregistre dans AssetPrice
     *
     * @param symbol Symbole de l'asset (ex: BTC, AAPL)
     * @return true si succès
     */
    private boolean updatePriceForSymbol(String symbol) {
        try {
            log.debug("Updating price for symbol: {}", symbol);

            // 1️⃣ Récupérer UN asset avec ce symbole (pour connaître le type)
            Asset asset = assetRepository.findBySymbol(symbol);

            if (asset == null) {
                log.warn("No asset found for symbol: {}", symbol);
                return false;
            }

            BigDecimal price = null;
            String source = null;

            // 2️⃣ Récupérer le prix selon le type d'asset
            if (asset.getAssetType() == AssetType.CRYPTO) {
                price = coinGeckoClient.getCryptoPrice(symbol, asset.getCurrency());
                source = "COINGECKO";
            } else if (asset.getAssetType() == AssetType.ACTION || asset.getAssetType() == AssetType.ETF) {
                price = alphaVantageClient.getStockPrice(symbol);
                source = "ALPHA_VANTAGE";
            }

            // 3️⃣ Si prix récupéré, le sauvegarder
            if (price != null) {
                saveAssetPrice(symbol, price, asset.getCurrency(), source);
                log.info("✓ Price updated for {}: {} (source: {})", symbol, price, source);
                return true;
            } else {
                log.warn("✗ Failed to retrieve price for symbol: {} ({})", symbol, asset.getAssetType());
                return false;
            }

        } catch (Exception e) {
            log.error("Error updating price for symbol {}: {}", symbol, e.getMessage());
            return false;
        }
    }

    /**
     * ⭐ Enregistre un nouveau prix dans AssetPrice
     * (Crée une NOUVELLE LIGNE avec le prix et la date du jour)
     */
    private void saveAssetPrice(String symbol, BigDecimal price, String currency, String source) {
        try {
            AssetPrice assetPrice = AssetPrice.builder()
                    .symbol(symbol)
                    .price(price)
                    .currency(currency)
                    .lastUpdated(LocalDateTime.now())
                    .source(source)
                    .build();

            assetPriceRepository.save(assetPrice);
            log.debug("Saved price in DB for {}: {} {}", symbol, price, currency);

        } catch (Exception e) {
            log.error("Error saving price for symbol {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * ⭐ Récupère le dernier prix d'un symbole
     */
    public Optional<AssetPrice> getLatestPrice(String symbol) {
        return assetPriceRepository.findLatestBySymbol(symbol);
    }

    /**
     * ⭐ Récupère les N derniers prix d'un symbole
     */
    public List<AssetPrice> getLatestPrices(String symbol, int limit) {
        return assetPriceRepository.findLatestPricesBySymbol(symbol, limit);
    }

    /**
     * ⭐ Récupère les prix sur une période
     */
    public List<AssetPrice> getPricesForPeriod(String symbol, LocalDateTime startDate, LocalDateTime endDate) {
        return assetPriceRepository.findBySymbolAndLastUpdatedBetween(symbol, startDate, endDate);
    }

    /**
     * ⭐ Forcer la mise à jour manuelle pour un symbole spécifique
     */
    public boolean refreshPriceForSymbol(String symbol) {
        log.info("Manual refresh requested for symbol: {}", symbol);
        return updatePriceForSymbol(symbol);
    }

    /**
     * ⭐ Récupère les statistiques
     */
    public Object getPricesCountBySymbol() {
        List<String> symbols = assetRepository.findAllDistinctSymbols();
        java.util.Map<String, Long> stats = new java.util.HashMap<>();

        for (String symbol : symbols) {
            long count = assetPriceRepository.countBySymbol(symbol);
            stats.put(symbol, count);
        }

        return stats;
    }
}
