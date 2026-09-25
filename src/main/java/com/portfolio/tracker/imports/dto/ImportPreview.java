package com.portfolio.tracker.imports.dto;

import com.portfolio.tracker.imports.ImportFormat;

import java.util.List;

/**
 * Aperçu d'un import : rien n'est encore enregistré.
 *
 * @param assets              actifs à associer à un symbole Yahoo (un par référence)
 * @param cashTrackingEnabled le portefeuille suit-il déjà ses liquidités ?
 *                            (sinon, les versements/retraits exigent de l'activer)
 */
public record ImportPreview(
        ImportFormat format,
        String formatLabel,
        boolean cashTrackingEnabled,
        List<ImportRowDto> rows,
        List<AssetResolutionDto> assets
) {}
