package com.portfolio.tracker.assetprice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.portfolio.tracker.asset.Asset;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetPriceRepository extends JpaRepository<AssetPrice, UUID> {

        @Query("SELECT ap FROM AssetPrice ap WHERE ap.symbol = :symbol ORDER BY ap.lastUpdated DESC LIMIT 1")
        Optional<AssetPrice> findLatestBySymbol(@Param("symbol") String symbol);

        @Query(value = "SELECT * FROM asset_prices WHERE symbol = :symbol ORDER BY last_updated DESC LIMIT :limit", nativeQuery = true)
        List<AssetPrice> findLatestPricesBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);

        @Query("SELECT DISTINCT ap.symbol FROM AssetPrice ap")
        List<String> findAllDistinctSymbols();

        long countBySymbol(String symbol);

        List<AssetPrice> findBySymbolAndLastUpdatedAfter(String symbol, LocalDateTime date);

        List<AssetPrice> findBySymbolAndLastUpdatedBetweenOrderByLastUpdatedAsc(
                        String symbol,
                        LocalDateTime start,
                        LocalDateTime end);

        List<AssetPrice> findBySymbolOrderByLastUpdatedDesc(String symbol);

        Optional<AssetPrice> findTopBySymbolOrderByLastUpdatedDesc(String symbol);

        @Query("SELECT a FROM AssetPrice a " +
                        "WHERE a.symbol = :symbol " +
                        "AND a.lastUpdated <= :date " +
                        "ORDER BY a.lastUpdated DESC " +
                        "LIMIT 1")
        Optional<AssetPrice> findTopBySymbolAndLastUpdatedLessThanEqualOrderByLastUpdatedDesc(
                        @Param("symbol") String symbol,
                        @Param("date") LocalDateTime date);

        @Query("SELECT a FROM AssetPrice a " +
                        "WHERE a.symbol = :symbol " +
                        "AND a.lastUpdated BETWEEN :startDate AND :endDate " +
                        "ORDER BY a.lastUpdated ASC")
        List<AssetPrice> findBySymbolAndLastUpdatedBetween(
                        @Param("symbol") String symbol,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);
}