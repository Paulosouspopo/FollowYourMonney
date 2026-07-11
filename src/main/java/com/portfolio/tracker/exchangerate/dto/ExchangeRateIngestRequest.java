package com.portfolio.tracker.exchangerate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeRateIngestRequest(

        @NotBlank(message = "La devise source est obligatoire")
        String fromCurrency,

        @NotBlank(message = "La devise cible est obligatoire")
        String toCurrency,

        @NotNull(message = "Le taux est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "Le taux doit être positif")
        BigDecimal rate,

        @NotNull(message = "La date de mise à jour est obligatoire")
        LocalDateTime lastUpdated,

        String source
) {}