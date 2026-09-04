package com.portfolio.tracker.assetprice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetPriceRepository extends JpaRepository<AssetPrice, UUID> {

    Optional<AssetPrice> findFirstBySymbolOrderByLastUpdatedDesc(String symbol);

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