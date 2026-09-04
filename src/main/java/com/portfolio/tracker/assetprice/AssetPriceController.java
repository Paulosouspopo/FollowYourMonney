package com.portfolio.tracker.assetprice;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/asset-prices")
@RequiredArgsConstructor
@Slf4j
public class AssetPriceController {

    private final AssetPriceService assetPriceService;

    /**
     * ⭐ Récupère le dernier prix d'un symbole
     * GET /api/asset-prices/latest/{symbol}
     */
    @GetMapping("/latest/{symbol}")
    public ResponseEntity<?> getLatestPrice(@PathVariable String symbol) {
        Optional<AssetPrice> price = assetPriceService.getLatestPrice(symbol);

        if (price.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(price.get());
    }

    /**
     * ⭐ Récupère les N derniers prix d'un symbole
     * GET /api/asset-prices/history/{symbol}?limit=30
     */
    @GetMapping("/history/{symbol}")
    public ResponseEntity<?> getPriceHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int limit) {

        if (limit <= 0 || limit > 365) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Limit must be between 1 and 365"));
        }

        List<AssetPrice> prices = assetPriceService.getLatestPrices(symbol, limit);
        return ResponseEntity.ok(prices);
    }

    /**
     * ⭐ Récupère les prix sur une période
     * GET /api/asset-prices/period/{symbol}?start=2024-01-01T00:00:00&end=2024-12-31T23:59:59
     */
    @GetMapping("/period/{symbol}")
    public ResponseEntity<?> getPricesForPeriod(
            @PathVariable String symbol,
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {

        if (start.isAfter(end)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Start date must be before end date"));
        }

        List<AssetPrice> prices = assetPriceService.getPricesForPeriod(symbol, start, end);
        return ResponseEntity.ok(prices);
    }

    /**
     * ⭐ Forcer la mise à jour manuelle pour un symbole
     * POST /api/asset-prices/refresh/{symbol}
     */
    @PostMapping("/refresh/{symbol}")
    public ResponseEntity<?> refreshPrice(@PathVariable String symbol) {
        boolean success = assetPriceService.refreshPriceForSymbol(symbol);

        if (!success) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Asset symbol not found: " + symbol));
        }

        return ResponseEntity.ok(Map.of("message", "Price refreshed for symbol: " + symbol));
    }

    /**
     * ⭐ Forcer la mise à jour manuelle pour TOUS les assets
     * POST /api/asset-prices/refresh-all
     */
    @PostMapping("/refresh-all")
    public ResponseEntity<?> refreshAllPrices() {
        log.info("Manual refresh-all requested");
        assetPriceService.updateAllAssetPrices();
        return ResponseEntity.ok(Map.of("message", "All prices refresh initiated"));
    }

    /**
     * ⭐ Health check
     * GET /api/asset-prices/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now()
        ));
    }
}