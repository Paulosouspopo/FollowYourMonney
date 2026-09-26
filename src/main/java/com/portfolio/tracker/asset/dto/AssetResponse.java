package com.portfolio.tracker.asset.dto;

import com.portfolio.tracker.asset.AssetType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        UUID portfolioId,
        String symbol,
        String name,
        String longName,
        String exchangeName,
        AssetType assetType,
        String currency,
        /** Actif non coté : valeurs saisies par l'utilisateur. */
        boolean manual,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}