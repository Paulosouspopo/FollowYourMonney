package com.portfolio.tracker.transaction;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
public class TransactionMapper {

    public Transaction toEntity(TransactionCreateRequest request,
                               Asset asset,
                               String baseCurrency,
                               BigDecimal exchangeRate) {

        BigDecimal fees = request.fees() != null ? request.fees() : BigDecimal.ZERO;
        String currency = request.currency() != null ? request.currency() : asset.getCurrency();

        BigDecimal totalAmount = request.quantity()
                .multiply(request.pricePerUnit())
                .setScale(2, RoundingMode.HALF_UP);

        // Les frais s'ajoutent au coût d'un achat, se déduisent du produit d'une vente
        BigDecimal gross = request.type() == TransactionType.SELL
                ? totalAmount.subtract(fees)
                : totalAmount.add(fees);

        return Transaction.builder()
                .asset(asset)
                .type(request.type())
                .quantity(request.quantity())
                .pricePerUnit(request.pricePerUnit())
                .fees(fees)
                .totalAmount(totalAmount)
                .currency(currency)
                .exchangeRate(exchangeRate)
                .baseCurrency(baseCurrency)
                .totalAmountInBaseCurrency(
                        gross.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP))
                .transactionDate(request.transactionDate() != null
                        ? request.transactionDate()
                        : LocalDateTime.now())
                .notes(request.notes())
                .build();
    }

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
                t.getExchangeRate(),
                t.getBaseCurrency(),
                t.getTotalAmountInBaseCurrency(),
                t.getTransactionDate(),
                t.getNotes(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
