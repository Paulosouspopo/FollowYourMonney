package com.portfolio.tracker.asset.manual;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Valeur d'une part d'un actif non coté à une date (valeur liquidative d'un relevé). */
public record ValuationRequest(
        @NotNull(message = "La date est obligatoire")
        @PastOrPresent(message = "La date ne peut pas être dans le futur")
        LocalDate date,

        @NotNull(message = "La valeur est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "La valeur doit être strictement positive")
        BigDecimal price
) {}
