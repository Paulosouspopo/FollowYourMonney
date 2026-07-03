package com.portfolio.tracker.portfolio.dto;

import com.portfolio.tracker.portfolio.PortfolioType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PortfolioCreateRequest(

        @NotNull(message = "L'utilisateur est obligatoire")
        UUID userId,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String name,

        @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
        String description,

        @NotNull(message = "Le type de portefeuille est obligatoire")
        PortfolioType type

) {}