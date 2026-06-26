package com.portfolio.tracker.repository;

import com.portfolio.tracker.entity.AssetPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetPriceRepository extends JpaRepository<AssetPrice, UUID> {
    Optional<AssetPrice> findBySymbol(String symbol);
    Optional<AssetPrice> findBySymbolAndCurrency(String symbol, String currency);
}
