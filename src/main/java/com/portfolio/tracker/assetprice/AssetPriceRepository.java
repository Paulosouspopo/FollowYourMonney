package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.assetprice.dto.LatestPriceProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetPriceRepository extends JpaRepository<AssetPrice, UUID> {

       // ------------------------------------------------- dernier prix (unitaire)

       /** Dernier prix connu pour un symbole. */
       Optional<AssetPrice> findTopBySymbolOrderByPriceDateDesc(String symbol);

       /** Dernier prix connu à une date donnée (ex : taux de change historique). */
       Optional<AssetPrice> findTopBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(
                     String symbol, LocalDate date);

       Optional<AssetPrice> findBySymbolAndPriceDate(String symbol, LocalDate priceDate);

       boolean existsBySymbol(String symbol);

       // ----------------------------------------------------- dernier prix (bulk)

       /**
        * Derniers prix de N symboles en UNE requête (DISTINCT ON PostgreSQL).
        * Élimine le N+1 de la valorisation.
        */
       @Query(value = """
                     SELECT DISTINCT ON (symbol)
                            symbol AS symbol, price AS price,
                            currency AS currency, last_updated AS lastUpdated
                     FROM asset_prices
                     WHERE symbol IN (:symbols)
                     ORDER BY symbol, price_date DESC
                     """, nativeQuery = true)
       List<LatestPriceProjection> findLatestForSymbols(@Param("symbols") Collection<String> symbols);

       /** Idem, mais au jour de {@code asOf} inclus (valorisation passée). */
       @Query(value = """
                     SELECT DISTINCT ON (symbol)
                            symbol AS symbol, price AS price,
                            currency AS currency, last_updated AS lastUpdated
                     FROM asset_prices
                     WHERE symbol IN (:symbols) AND price_date <= CAST(:asOf AS date)
                     ORDER BY symbol, price_date DESC
                     """, nativeQuery = true)
       List<LatestPriceProjection> findLatestForSymbolsAsOf(@Param("symbols") Collection<String> symbols,
                     @Param("asOf") LocalDateTime asOf);

       // --------------------------------------------------------------- historique

       /** Séries de N symboles sur une plage de jours, en une requête. */
       List<AssetPrice> findBySymbolInAndPriceDateBetweenOrderByPriceDateAsc(
                     Collection<String> symbols, LocalDate from, LocalDate to);

       /**
        * Dernier point strictement antérieur à {@code before}, pour chaque symbole :
        * sert d'amorce quand une plage commence un jour sans cotation.
        */
       @Query(value = """
                     SELECT DISTINCT ON (symbol) *
                     FROM asset_prices
                     WHERE symbol IN (:symbols) AND price_date < :before
                     ORDER BY symbol, price_date DESC
                     """, nativeQuery = true)
       List<AssetPrice> findLastBeforeForSymbols(@Param("symbols") Collection<String> symbols,
                     @Param("before") LocalDate before);

       List<AssetPrice> findBySymbolAndPriceDateBetween(String symbol, LocalDate from, LocalDate to);

       List<AssetPrice> findBySymbolAndLastUpdatedBetweenOrderByLastUpdatedAsc(
                     String symbol, LocalDateTime start, LocalDateTime end);

       @Query(value = "SELECT * FROM asset_prices WHERE symbol = :symbol ORDER BY price_date DESC LIMIT :limit", nativeQuery = true)
       List<AssetPrice> findLatestPricesBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);

       // ---------------------------------------------------------------- métriques

       long countBySymbol(String symbol);
}
