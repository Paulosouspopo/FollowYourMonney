package com.portfolio.tracker.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Récupérer les transactions d'un asset POUR UN USER SPÉCIFIQUE
    @Query("SELECT t FROM Transaction t " +
            "WHERE t.asset.symbol = :symbol AND t.asset.portfolio.user.id = :userId " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findByAssetSymbolAndUserId(@Param("symbol") String symbol, @Param("userId") UUID userId);

    @Query("SELECT t FROM Transaction t WHERE t.id = :transactionId AND t.asset.portfolio.user.id = :userId")
    Optional<Transaction> findByIdAndUserId(@Param("transactionId") UUID transactionId, @Param("userId") UUID userId);
}
