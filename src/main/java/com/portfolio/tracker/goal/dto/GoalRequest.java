package com.portfolio.tracker.goal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** @param portfolioId null = tout le patrimoine */
public record GoalRequest(
        @NotBlank(message = "Donne un nom à ton objectif") @Size(max = 100) String name,
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "1", message = "Le montant doit être d'au moins 1 €") BigDecimal targetAmount,
        LocalDate targetDate,
        UUID portfolioId
) {}
