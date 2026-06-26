package com.portfolio.tracker.service;

import com.portfolio.tracker.entity.AssetPrice;
import com.portfolio.tracker.repository.AssetPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetPriceService {

    private final AssetPriceRepository assetPriceRepository;

    public AssetPrice findBySymbol(String symbol) {
        return assetPriceRepository.findBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Price not found for symbol: " + symbol));
    }

    public Optional<AssetPrice> findBySymbolAndCurrency(String symbol, String currency) {
        return assetPriceRepository.findBySymbolAndCurrency(symbol, currency);
    }

    public AssetPrice save(AssetPrice assetPrice) {
        return assetPriceRepository.save(assetPrice);
    }

    public void deleteById(UUID id) {
        assetPriceRepository.deleteById(id);
    }
}
