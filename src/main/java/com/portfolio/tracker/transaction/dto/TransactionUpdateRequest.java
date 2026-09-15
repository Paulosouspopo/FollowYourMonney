package com.portfolio.tracker.transaction.dto;

import com.portfolio.tracker.transaction.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * La devise n'est volontairement pas modifiable : changer la devise d'une
 * opération passée invaliderait le taux historique figé. Il faut supprimer
 * puis recréer la transaction.
 */
public record TransactionUpdateRequest(

        @NotNull(message = "Le type de transaction est obligatoire")
        TransactionType type,

        @NotNull(message = "La quantité est obligatoire")
        @DecimalMin(value = "0.0", message = "La quantité doit être positive ou nulle")
        BigDecimal quantity,

        @NotNull(message = "Le prix par unité est obligatoire")
        @DecimalMin(value = "0.0", message = "Le prix par unité doit être positif ou nul")
        BigDecimal pricePerUnit,

        @DecimalMin(value = "0.0", message = "Les frais doivent être positifs ou nuls")
        BigDecimal fees,

        @PastOrPresent(message = "La date de transaction ne peut pas être dans le futur")
        LocalDateTime transactionDate,

        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères")
        String notes
) {}