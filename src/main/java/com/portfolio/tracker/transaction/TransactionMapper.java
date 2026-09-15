package com.portfolio.tracker.transaction;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction t) {
        Asset asset = t.getAsset();
        return new TransactionResponse(
                t.getId(),
                asset.getId(),
                asset.getPortfolio().getId(),
                asset.getSymbol(),
                asset.getName(),
                t.getType(),
                t.getQuantity(),
                t.getPricePerUnit(),
                t.getFees(),
                t.getTotalAmount(),
                t.getCurrency(),
                t.getExchangeRateToEur(),
                t.getTotalAmountEur(),
                t.getFeesEur(),
                t.getTransactionDate(),
                t.getNotes(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}