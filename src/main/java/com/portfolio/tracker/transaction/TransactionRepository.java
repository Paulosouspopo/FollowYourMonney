package com.portfolio.tracker.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByAssetId(UUID assetId);

    @Query("SELECT t FROM Transaction t WHERE t.id = :transactionId AND t.asset.portfolio.user.id = :userId")
    Optional<Transaction> findByIdAndUserId(@Param("transactionId") UUID transactionId, @Param("userId") UUID userId);
}
