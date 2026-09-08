package com.portfolio.tracker.asset.dto;

import com.portfolio.tracker.asset.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record AssetCreateRequest(

        @NotNull(message = "L'id du portfolio est obligatoire")
        UUID portfolioId,

        @NotBlank(message = "Le symbole est obligatoire")
        @Pattern(
                regexp = "^[A-Za-z0-9.\\-=]+$",
                message = "Le symbole ne doit contenir que des lettres, chiffres, points, tirets ou '='"
        )
        String symbol,

        @NotBlank(message = "Le nom est obligatoire")
        String name,

        @NotNull(message = "Le type d'actif est obligatoire")
        AssetType assetType,

        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "La devise doit être un code ISO 4217 en majuscules (ex: USD, EUR)"
        )
        String currency
) {}
