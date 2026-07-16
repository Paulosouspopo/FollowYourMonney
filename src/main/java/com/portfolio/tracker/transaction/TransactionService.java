package com.portfolio.tracker.transaction;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import com.portfolio.tracker.transaction.dto.TransactionUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AssetRepository assetRepository;
    private final TransactionMapper transactionMapper;

    public List<TransactionResponse> findByAssetId(UUID assetId, UUID userId) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset", assetId);
        }
        return transactionRepository.findByAssetId(assetId).stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    public TransactionResponse findByIdAndUserId(UUID transactionId, UUID userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction non accessible"));

        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse create(TransactionCreateRequest request, UUID userId) {
        Asset asset = assetRepository.findByIdAndUserId(request.assetId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset non accessible"));

        BigDecimal totalAmount = request.quantity().multiply(request.pricePerUnit());

        Transaction transaction = transactionMapper.toEntity(request, asset, totalAmount);
        Transaction saved = transactionRepository.save(transaction);
        return transactionMapper.toResponse(saved);
    }

    @Transactional
    public TransactionResponse update(UUID transactionId, TransactionUpdateRequest request, UUID userId) {
        Transaction existing = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction non accessible"));

        BigDecimal totalAmount = request.quantity().multiply(request.pricePerUnit());

        existing.setType(request.type());
        existing.setQuantity(request.quantity());
        existing.setPricePerUnit(request.pricePerUnit());
        existing.setTotalAmount(totalAmount);
        existing.setCurrency(request.currency());
        existing.setTransactionDate(request.transactionDate());
        existing.setNotes(request.notes());

        Transaction saved = transactionRepository.save(existing);
        return transactionMapper.toResponse(saved);
    }

    @Transactional
    public void deleteById(UUID transactionId, UUID userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction non accessible"));

        transactionRepository.delete(transaction);
    }
}
