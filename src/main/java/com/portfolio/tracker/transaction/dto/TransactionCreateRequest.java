package com.portfolio.tracker.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.portfolio.tracker.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TransactionCreateRequest(

        @NotNull(message = "L'id du portefeuille est obligatoire")
        UUID portfolioId,

        @NotBlank(message = "Le symbole est obligatoire")
        @Size(max = 20, message = "Le symbole ne doit pas dépasser 20 caractères")
        String symbol,

        @NotNull(message = "Le type de transaction est obligatoire")
        TransactionType type,

        @NotNull(message = "La quantité est obligatoire")
        @DecimalMin(value = "0.0", inclusive = true, message = "La quantité doit être positive ou nulle")
        BigDecimal quantity,

        @NotNull(message = "Le prix par unité est obligatoire")
        @DecimalMin(value = "0.0", inclusive = true, message = "Le prix par unité doit être positif ou nul")
        BigDecimal pricePerUnit,

        @DecimalMin(value = "0.0", message = "Les frais doivent être positifs ou nuls")
        BigDecimal fees,

        @Pattern(regexp = "^[A-Z]{3}$", message = "La devise doit être un code ISO 4217 (ex: EUR)")
        String currency,

        @PastOrPresent(message = "La date de transaction ne peut pas être dans le futur")
        LocalDateTime transactionDate,

        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères")
        String notes
) {}
