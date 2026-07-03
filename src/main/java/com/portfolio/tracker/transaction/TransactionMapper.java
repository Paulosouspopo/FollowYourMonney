package com.portfolio.tracker.transaction;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionMapper {

    public Transaction toEntity(TransactionCreateRequest request, Asset asset, BigDecimal totalAmount) {
        return Transaction.builder()
                .asset(asset)
                .type(request.type())
                .quantity(request.quantity())
                .pricePerUnit(request.pricePerUnit())
                .totalAmount(totalAmount)
                .currency(request.currency())
                .transactionDate(request.transactionDate())
                .notes(request.notes())
                .build();
    }

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAsset().getId(),
                transaction.getType(),
                transaction.getQuantity(),
                transaction.getPricePerUnit(),
                transaction.getTotalAmount(),
                transaction.getCurrency(),
                transaction.getTransactionDate(),
                transaction.getNotes(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
