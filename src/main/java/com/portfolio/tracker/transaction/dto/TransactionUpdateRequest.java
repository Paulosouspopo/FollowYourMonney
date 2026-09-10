package com.portfolio.tracker.transaction.dto;

import com.portfolio.tracker.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionUpdateRequest(

        @NotNull(message = "Le type de transaction est obligatoire")
        TransactionType type,

        @NotNull(message = "La quantité est obligatoire")
        @DecimalMin(value = "0.0", inclusive = true, message = "La quantité doit être positive ou nulle")
        BigDecimal quantity,

        @NotNull(message = "Le prix unitaire est obligatoire")
        @DecimalMin(value = "0.0", inclusive = true, message = "Le prix unitaire doit être positif ou nul")
        BigDecimal pricePerUnit,

        @DecimalMin(value = "0.0", message = "Les frais doivent être positifs ou nuls")
        BigDecimal fees,

        @PastOrPresent(message = "La date de transaction ne peut pas être dans le futur")
        LocalDateTime transactionDate,

        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères")
        String notes
) {}

