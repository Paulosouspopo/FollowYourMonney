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

    public TransactionResponse findById(UUID id) {
        return transactionMapper.toResponse(getTransactionOrThrow(id));
    }

    public List<TransactionResponse> findByAssetId(UUID assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset", assetId);
        }
        return transactionRepository.findByAssetId(assetId).stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Transactional
    public TransactionResponse create(TransactionCreateRequest request) {
        Asset asset = assetRepository.findById(request.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", request.assetId()));

        BigDecimal totalAmount = request.quantity().multiply(request.pricePerUnit());

        Transaction transaction = transactionMapper.toEntity(request, asset, totalAmount);
        Transaction saved = transactionRepository.save(transaction);
        return transactionMapper.toResponse(saved);
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionUpdateRequest request) {
        Transaction existing = getTransactionOrThrow(id);

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
    public void deleteById(UUID id) {
        if (!transactionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Transaction", id);
        }
        transactionRepository.deleteById(id);
    }

    private Transaction getTransactionOrThrow(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    }
}
