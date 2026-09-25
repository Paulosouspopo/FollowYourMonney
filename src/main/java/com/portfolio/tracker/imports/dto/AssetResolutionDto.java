package com.portfolio.tracker.imports.dto;

import com.portfolio.tracker.marketdata.AssetSearchResult;

/**
 * Proposition de symbole Yahoo pour un actif du relevé.
 *
 * @param reference   clé de l'actif dans le relevé (renvoyée telle quelle à la validation)
 * @param label       libellé du relevé
 * @param isin        ISIN s'il est connu
 * @param suggestion  symbole proposé, null si rien trouvé
 * @param confidence  REMEMBERED (déjà validé lors d'un import précédent),
 *                    CERTAIN (ISIN ou paire crypto exacte), TO_CONFIRM
 *                    (recherche par nom : à vérifier), NOT_FOUND
 */
public record AssetResolutionDto(
        String reference,
        String label,
        String isin,
        AssetSearchResult suggestion,
        Confidence confidence
) {
    public enum Confidence { REMEMBERED, CERTAIN, TO_CONFIRM, NOT_FOUND }
}
