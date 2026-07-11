package com.portfolio.tracker.recurringinvestment.dto;

import com.portfolio.tracker.recurringinvestment.Frequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecurringInvestmentCreateRequest(

        @NotNull(message = "L'id de l'asset est obligatoire")
        UUID assetId,

        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0.01", message = "Le montant doit être positif")
        java.math.BigDecimal amount,

        @NotNull(message = "La fréquence est obligatoire")
        Frequency frequency,

        @NotNull(message = "La date de début est obligatoire")
        LocalDateTime startDate,

        LocalDateTime endDate
) {}