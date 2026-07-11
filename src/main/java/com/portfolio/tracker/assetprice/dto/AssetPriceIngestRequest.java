package com.portfolio.tracker.assetprice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssetPriceIngestRequest(

        @NotBlank(message = "Le symbole est obligatoire")
        String symbol,

        @NotNull(message = "Le prix est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "Le prix doit être positif")
        BigDecimal price,

        String currency,

        @NotNull(message = "La date de mise à jour est obligatoire")
        LocalDateTime lastUpdated,

        String source
) {}