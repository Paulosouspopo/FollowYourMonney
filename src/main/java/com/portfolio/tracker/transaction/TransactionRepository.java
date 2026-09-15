package com.portfolio.tracker.transaction;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("SELECT t FROM Transaction t WHERE t.id = :transactionId AND t.asset.portfolio.user.id = :userId")
    Optional<Transaction> findByIdAndUserId(@Param("transactionId") UUID transactionId,
                                            @Param("userId") UUID userId);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.asset.symbol = :symbol AND t.asset.portfolio.user.id = :userId
            ORDER BY t.transactionDate DESC
            """)
    List<Transaction> findByAssetSymbolAndUserId(@Param("symbol") String symbol,
                                                 @Param("userId") UUID userId);

    @Query("SELECT t FROM Transaction t WHERE t.asset.id = :assetId AND t.asset.portfolio.user.id = :userId")
    List<Transaction> findByAssetIdAndUserId(@Param("assetId") UUID assetId,
                                             @Param("userId") UUID userId);

    /**
     * REQUÊTE CENTRALE DE VALORISATION.
     *
     * Charge en UNE fois toutes les transactions nécessaires, avec asset et
     * portfolio déjà hydratés (JOIN FETCH) pour supprimer le N+1.
     *
     * @param portfolioId filtre optionnel : null = tous les portefeuilles
     * @param asOf        borne temporelle optionnelle : null = jusqu'à maintenant
     */
    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.asset a
            JOIN FETCH a.portfolio p
            WHERE p.user.id = :userId
              AND (:portfolioId IS NULL OR p.id = :portfolioId)
              AND (:asOf IS NULL OR t.transactionDate <= :asOf)
            ORDER BY t.transactionDate ASC
            """)
    List<Transaction> findAllForValuation(@Param("userId") UUID userId,
                                          @Param("portfolioId") UUID portfolioId,
                                          @Param("asOf") LocalDateTime asOf);

    /** Transactions récentes de l'utilisateur, paginées. */
    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.asset a
            JOIN FETCH a.portfolio p
            WHERE p.user.id = :userId
            ORDER BY t.transactionDate DESC
            """)
    List<Transaction> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

    /** Date de la première opération : point de départ d'un backfill. */
    @Query("""
            SELECT MIN(t.transactionDate) FROM Transaction t
            WHERE t.asset.portfolio.id = :portfolioId
            """)
    Optional<LocalDateTime> findFirstTransactionDate(@Param("portfolioId") UUID portfolioId);
}