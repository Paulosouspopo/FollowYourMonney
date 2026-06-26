package com.portfolio.tracker.repository;

import com.portfolio.tracker.entity.Transaction;
import com.portfolio.tracker.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByAssetId(UUID assetId);
    List<Transaction> findByAssetIdAndType(UUID assetId, TransactionType type);
    List<Transaction> findByAssetIdAndTransactionDateBetween(UUID assetId, LocalDateTime start, LocalDateTime end);
}
