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

        /** Transactions d'un actif d'UN portefeuille (le même symbole peut exister ailleurs). */
        @Query("""
                        SELECT t FROM Transaction t
                        JOIN FETCH t.asset a
                        WHERE a.symbol = :symbol AND a.portfolio.id = :portfolioId
                        AND a.portfolio.user.id = :userId
                        ORDER BY t.transactionDate DESC, t.createdAt DESC
                        """)
        List<Transaction> findByAssetSymbolAndPortfolioIdAndUserId(@Param("symbol") String symbol,
                        @Param("portfolioId") UUID portfolioId,
                        @Param("userId") UUID userId);

        /** Toutes les transactions d'un portefeuille, plus récentes d'abord. */
        @Query("""
                        SELECT t FROM Transaction t
                        JOIN FETCH t.asset a
                        WHERE a.portfolio.id = :portfolioId AND a.portfolio.user.id = :userId
                        ORDER BY t.transactionDate DESC, t.createdAt DESC
                        """)
        List<Transaction> findByPortfolioIdAndUserId(@Param("portfolioId") UUID portfolioId,
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
        @Query(value = """
                        SELECT t.* FROM transactions t
                        JOIN assets a ON a.id = t.asset_id
                        JOIN portfolios p ON p.id = a.portfolio_id
                        WHERE p.user_id = :userId
                        AND (CAST(:portfolioId AS uuid) IS NULL OR p.id = CAST(:portfolioId AS uuid))
                        AND (CAST(:asOf AS timestamp) IS NULL OR t.transaction_date <= CAST(:asOf AS timestamp))
                        ORDER BY t.transaction_date ASC, t.created_at ASC
                        """, nativeQuery = true)
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

        /**
         * Toutes les transactions d'un portefeuille, asset hydraté, dans l'ordre
         * chronologique : entrée unique de la reconstruction de l'historique.
         */
        @Query("""
                        SELECT t FROM Transaction t
                        JOIN FETCH t.asset a
                        WHERE a.portfolio.id = :portfolioId
                        ORDER BY t.transactionDate ASC, t.createdAt ASC
                        """)
        List<Transaction> findAllByPortfolioIdForHistory(@Param("portfolioId") UUID portfolioId);

        /** Date de la première opération : point de départ d'un backfill. */
        @Query("""
                        SELECT MIN(t.transactionDate) FROM Transaction t
                        WHERE t.asset.portfolio.id = :portfolioId
                        """)
        Optional<LocalDateTime> findFirstTransactionDate(@Param("portfolioId") UUID portfolioId);

        boolean existsByExternalRef(String externalRef);
}
