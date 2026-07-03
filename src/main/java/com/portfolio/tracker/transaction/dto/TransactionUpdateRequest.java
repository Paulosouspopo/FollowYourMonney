package com.portfolio.tracker.transaction.dto;

import com.portfolio.tracker.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionUpdateRequest(

        @NotNull(message = "Le type de transaction est obligatoire")
        TransactionType type,

        @NotNull(message = "La quantité est obligatoire")
        @DecimalMin(value = "0.00000001", message = "La quantité doit être positive")
        BigDecimal quantity,

        @NotNull(message = "Le prix unitaire est obligatoire")
        @DecimalMin(value = "0.0", inclusive = true, message = "Le prix unitaire doit être positif ou nul")
        BigDecimal pricePerUnit,

        String currency,

        LocalDateTime transactionDate,

        String notes
) {}
