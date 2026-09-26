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
        BigDecimal annualInterestRate,

        /** Date d'ouverture du compte (facultative ; PEA : départ des 5 ans). */
        @jakarta.validation.constraints.PastOrPresent(message = "La date d'ouverture ne peut pas être dans le futur")
        java.time.LocalDate openedAt,

        /** Compte multidevise (opérations réglées dans leur devise) ; null = non. Exige le suivi des liquidités. */
        Boolean multiCurrencyCash
) {
    /** Sans date d'ouverture. */
    public PortfolioCreateRequest(String name, String description, PortfolioType type, Boolean cashTracking,
            BigDecimal annualInterestRate) {
        this(name, description, type, cashTracking, annualInterestRate, null, null);
    }

    /** Compte en euros. */
    public PortfolioCreateRequest(String name, String description, PortfolioType type, Boolean cashTracking,
            BigDecimal annualInterestRate, java.time.LocalDate openedAt) {
        this(name, description, type, cashTracking, annualInterestRate, openedAt, null);
    }
}
