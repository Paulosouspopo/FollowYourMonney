package com.portfolio.tracker.imports.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validation d'un import.
 *
 * @param enableCashTracking active le suivi des liquidités si des mouvements d'argent sont importés
 * @param assets             référence d'actif → symbole Yahoo choisi (mémorisé pour les prochains imports)
 * @param rows               lignes cochées de l'aperçu
 */
public record ImportCommitRequest(
        @NotNull(message = "Le portefeuille est obligatoire")
        UUID portfolioId,
        boolean enableCashTracking,
        Map<String, String> assets,
        @NotEmpty(message = "Aucune ligne à importer")
        List<ImportRowDto> rows
) {}
