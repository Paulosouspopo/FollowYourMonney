package com.portfolio.tracker.imports.dto;

import com.portfolio.tracker.imports.ImportKind;
import com.portfolio.tracker.imports.RowStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ligne de l'aperçu, renvoyée telle quelle (lignes cochées) à la validation.
 *
 * @param id                 rang dans l'aperçu
 * @param lines              lignes du fichier d'origine
 * @param assetReference     clé de l'actif (voir {@link AssetResolutionDto#reference()}), null pour un mouvement d'argent
 * @param unitPrice          null tant qu'un prix estimé n'a pas pu être calculé
 * @param amount             montant d'un mouvement d'argent (EUR)
 * @param priceEstimated     conversion crypto → crypto : prix recalculé à la validation
 * @param valuationReference actif servant à l'estimation
 */
public record ImportRowDto(
        int id,
        List<Integer> lines,
        LocalDateTime dateTime,
        ImportKind kind,
        String assetReference,
        String assetLabel,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal fees,
        String currency,
        BigDecimal amount,
        String notes,
        String externalRef,
        RowStatus status,
        String message,
        boolean priceEstimated,
        String valuationReference,
        BigDecimal valuationQuantity
) {}
