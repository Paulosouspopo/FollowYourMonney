package com.portfolio.tracker.cash.dto;

import com.portfolio.tracker.cash.CashMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Création comme modification : un mouvement est entièrement redéfini.
 * Devise absente = EUR. Change (CONVERSION) : {@code amount} en
 * {@code currency} contre {@code counterAmount} en {@code counterCurrency}.
 */
public record CashMovementRequest(

        @NotNull(message = "Le type de mouvement est obligatoire")
        CashMovementType type,

        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "Le montant doit être strictement positif")
        BigDecimal amount,

        @NotNull(message = "La date est obligatoire")
        @PastOrPresent(message = "La date ne peut pas être dans le futur")
        LocalDate movementDate,

        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères")
        String notes,

        @Pattern(regexp = "^[A-Z]{3}$", message = "La devise doit être un code ISO 4217 (ex: USD)")
        String currency,

        @DecimalMin(value = "0.0", inclusive = false, message = "Le montant reçu doit être strictement positif")
        BigDecimal counterAmount,

        @Pattern(regexp = "^[A-Z]{3}$", message = "La devise doit être un code ISO 4217 (ex: USD)")
        String counterCurrency
) {

    /** Mouvement en euros (saisie simple, import, plans). */
    public CashMovementRequest(CashMovementType type, BigDecimal amount, LocalDate movementDate, String notes) {
        this(type, amount, movementDate, notes, null, null, null);
    }
}
