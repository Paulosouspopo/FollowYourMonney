package com.portfolio.tracker.recurringinvestment.dto;

import com.portfolio.tracker.recurringinvestment.Frequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecurringInvestmentUpdateRequest(

        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0.01", message = "Le montant doit être positif")
        BigDecimal amount,

        @NotNull(message = "La fréquence est obligatoire")
        Frequency frequency,

        LocalDateTime endDate,

        @NotNull(message = "Le statut actif est obligatoire")
        Boolean active
) {}