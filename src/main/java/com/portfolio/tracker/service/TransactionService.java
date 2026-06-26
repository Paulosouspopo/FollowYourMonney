package com.portfolio.tracker.service;

import com.portfolio.tracker.entity.Transaction;
import com.portfolio.tracker.enums.TransactionType;
import com.portfolio.tracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public Transaction findById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));
    }

    public List<Transaction> findByAssetId(UUID assetId) {
        return transactionRepository.findByAssetId(assetId);
    }

    public List<Transaction> findByAssetIdAndType(UUID assetId, TransactionType type) {
        return transactionRepository.findByAssetIdAndType(assetId, type);
    }

    public List<Transaction> findByAssetIdAndDateBetween(UUID assetId, LocalDateTime start, LocalDateTime end) {
        return transactionRepository.findByAssetIdAndTransactionDateBetween(assetId, start, end);
    }

    public Transaction save(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    public void deleteById(UUID id) {
        transactionRepository.deleteById(id);
    }
}
