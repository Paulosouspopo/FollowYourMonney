package com.portfolio.tracker.asset.manual;

import com.portfolio.tracker.asset.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Actif non coté : un nom, un type, une devise. Le symbole est attribué par le serveur. */
public record ManualAssetRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 120, message = "Le nom ne doit pas dépasser 120 caractères")
        String name,

        @NotNull(message = "Le type est obligatoire")
        AssetType assetType,

        @Pattern(regexp = "^[A-Z]{3}$", message = "La devise doit être un code ISO 4217 (ex: EUR)")
        String currency
) {}
