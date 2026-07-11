package com.portfolio.tracker.assetprice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetPriceRepository extends JpaRepository<AssetPrice, UUID> {

    Optional<AssetPrice> findFirstBySymbolOrderByLastUpdatedDesc(String symbol);

    List<AssetPrice> findBySymbolAndLastUpdatedBetweenOrderByLastUpdatedAsc(
            String symbol,
            LocalDateTime start,
            LocalDateTime end
    );

    List<AssetPrice> findBySymbolOrderByLastUpdatedDesc(String symbol);
}