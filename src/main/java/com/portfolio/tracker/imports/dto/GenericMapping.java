package com.portfolio.tracker.imports.dto;

import com.portfolio.tracker.imports.ImportKind;

import java.util.Map;

/**
 * Association des colonnes d'un relevé inconnu (import générique).
 * Les colonnes sont désignées par leur nom d'en-tête ; null = absente.
 *
 * @param typeValues      valeur de la colonne type → nature de l'opération
 *                        (ex : « Achat » → BUY). Une valeur absente est ignorée.
 * @param assetColumn     ISIN, symbole ou nom de l'actif (résolu ensuite via Yahoo)
 * @param priceColumn     prix unitaire ; à défaut, montant / quantité
 * @param amountColumn    montant total (obligatoire pour un mouvement d'argent ou un dividende)
 * @param defaultCurrency devise si aucune colonne devise (EUR par défaut)
 */
public record GenericMapping(
        String dateColumn,
        String typeColumn,
        Map<String, ImportKind> typeValues,
        String assetColumn,
        String quantityColumn,
        String priceColumn,
        String amountColumn,
        String feesColumn,
        String currencyColumn,
        String defaultCurrency
) {}
