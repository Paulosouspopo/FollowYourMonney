package com.portfolio.tracker.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.portfolio.tracker.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record TransactionCreateRequest(

        @NotNull(message = "L'id de l'asset est obligatoire") 
        UUID assetId,

        @NotNull(message = "Le type de transaction est obligatoire") 
        TransactionType type,

        @NotNull(message = "La quantité est obligatoire") 
        @DecimalMin(value = "0.00000001", message = "La quantité doit être positive") 
        BigDecimal quantity,

        @NotNull(message = "Le prix par unité est obligatoire") 
        @DecimalMin(value = "0.00000001", inclusive = true, message = "Le prix par unité doit être positif") 
        BigDecimal pricePerUnit,

        String currency,

        LocalDateTime transactionDate,

        String notes) {}
