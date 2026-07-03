package com.portfolio.tracker.asset.dto;

import com.portfolio.tracker.asset.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssetUpdateRequest(

        @NotBlank(message = "Le symbole est obligatoire")
        String symbol,

        @NotBlank(message = "Le nom est obligatoire")
        String name,

        @NotNull(message = "Le type d'actif est obligatoire")
        AssetType assetType,

        String currency
) {}