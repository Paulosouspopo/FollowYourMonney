package com.portfolio.tracker.portfolio.dto;

import com.portfolio.tracker.portfolio.PortfolioType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PortfolioCreateRequest(

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String name,

        @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
        String description,

        @NotNull(message = "Le type de portefeuille est obligatoire")
        PortfolioType type,

        /** Suivi des liquidités ; null = désactivé (toujours actif pour un livret). */
        Boolean cashTracking,

        @DecimalMin(value = "0.0", message = "Le taux doit être positif ou nul")
        @DecimalMax(value = "100.0", message = "Le taux doit être inférieur à 100 %")
        BigDecimal annualInterestRate

) {}
