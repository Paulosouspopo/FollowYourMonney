package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.assetprice.dto.LatestPriceProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetPriceRepository extends JpaRepository<AssetPrice, UUID> {

       // ------------------------------------------------- dernier prix (unitaire)

       /** Dernier prix connu pour un symbole. */
       Optional<AssetPrice> findTopBySymbolOrderByLastUpdatedDesc(String symbol);

       /** Dernier prix connu à une date donnée (valorisation historique). */
       @Query("""
                     SELECT ap FROM AssetPrice ap
                     WHERE ap.symbol = :symbol AND ap.lastUpdated <= :asOf
                     ORDER BY ap.lastUpdated DESC
                     LIMIT 1
                     """)
       Optional<AssetPrice> findLatestBySymbolAsOf(@Param("symbol") String symbol,
                     @Param("asOf") LocalDateTime asOf);

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
                     ORDER BY symbol, last_updated DESC
                     """, nativeQuery = true)
       List<LatestPriceProjection> findLatestForSymbols(@Param("symbols") Collection<String> symbols);

       /** Idem, mais à une date donnée (backfill / snapshots passés). */
       @Query(value = """
                     SELECT DISTINCT ON (symbol)
                            symbol AS symbol, price AS price,
                            currency AS currency, last_updated AS lastUpdated
                     FROM asset_prices
                     WHERE symbol IN (:symbols) AND last_updated <= :asOf
                     ORDER BY symbol, last_updated DESC
                     """, nativeQuery = true)
       List<LatestPriceProjection> findLatestForSymbolsAsOf(@Param("symbols") Collection<String> symbols,
                     @Param("asOf") LocalDateTime asOf);

       // --------------------------------------------------------------- historique

       /** Série temporelle d'un symbole, paginée. */
       List<AssetPrice> findBySymbolAndLastUpdatedBetweenOrderByLastUpdatedAsc(
                     String symbol, LocalDateTime start, LocalDateTime end);

       /** Historique récent paginé (remplace findLatestPricesBySymbol + limit). */
       List<AssetPrice> findBySymbolOrderByLastUpdatedDesc(String symbol, Pageable pageable);

       /** Bornes de l'historique disponible, pour dimensionner un backfill. */
       @Query("SELECT MIN(ap.lastUpdated) FROM AssetPrice ap")
       Optional<LocalDateTime> findEarliestPriceDate();

       // ---------------------------------------------------------------- métriques

       @Query("SELECT DISTINCT ap.symbol FROM AssetPrice ap")
       List<String> findAllDistinctSymbols();

       long countBySymbol(String symbol);

       @Query(value = "SELECT * FROM asset_prices WHERE symbol = :symbol ORDER BY last_updated DESC LIMIT :limit", nativeQuery = true)
       List<AssetPrice> findLatestPricesBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);

       @Query("SELECT MIN(ap.lastUpdated) FROM AssetPrice ap WHERE ap.symbol = :symbol")
       Optional<LocalDateTime> findEarliestPriceDateBySymbol(@Param("symbol") String symbol);
}