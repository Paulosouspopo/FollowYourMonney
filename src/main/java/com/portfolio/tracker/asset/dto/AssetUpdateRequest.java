package com.portfolio.tracker.asset.dto;

import jakarta.validation.constraints.NotBlank;

public record AssetUpdateRequest(
        @NotBlank(message = "Le nom est obligatoire")
        String name,

        String currency
) {}